package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.CreatePaymentRequest;
import com.ravan.SpringBootLab.dto.PaymentResponse;
import com.ravan.SpringBootLab.exception.IdempotencyKeyRequiredException;
import com.ravan.SpringBootLab.exception.InvalidOrderStatusException;
import com.ravan.SpringBootLab.exception.OrderAlreadyPaidException;
import com.ravan.SpringBootLab.exception.OrderNotFoundException;
import com.ravan.SpringBootLab.exception.PaymentNotFoundException;
import com.ravan.SpringBootLab.model.IdempotencyRecord;
import com.ravan.SpringBootLab.model.Order;
import com.ravan.SpringBootLab.model.OrderStatus;
import com.ravan.SpringBootLab.model.Payment;
import com.ravan.SpringBootLab.model.PaymentMethod;
import com.ravan.SpringBootLab.model.PaymentStatus;
import com.ravan.SpringBootLab.repository.IdempotencyRecordRepository;
import com.ravan.SpringBootLab.repository.OrderRepository;
import com.ravan.SpringBootLab.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private IdempotencyRecordRepository idempotencyRecordRepository;
    private OutboxEventService outboxEventService;
    private PaymentService paymentService;
    private CreatePaymentRequest request;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        idempotencyRecordRepository = mock(IdempotencyRecordRepository.class);
        outboxEventService = mock(OutboxEventService.class);
        request = mock(CreatePaymentRequest.class);

        when(request.getMethod()).thenReturn(PaymentMethod.CREDIT_CARD);

        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                idempotencyRecordRepository,
                outboxEventService
        );
    }

    @Test
    void rejectsNullAndBlankIdempotencyKeysBeforeRepositoryAccess() {
        assertThatThrownBy(() -> paymentService.payOrder(10, request, null))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        assertThatThrownBy(() -> paymentService.payOrder(10, request, "   "))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        verify(idempotencyRecordRepository, never())
                .findByIdempotencyKeyAndRequestPath(any(), any());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void returnsExistingPaymentForRepeatedIdempotencyKey() {
        Order order = pendingOrder(10);
        Payment payment = paidPayment(20, order);
        IdempotencyRecord record = new IdempotencyRecord(
                "payment-key",
                "/api/orders/10/payments",
                20
        );

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath(
                "payment-key",
                "/api/orders/10/payments"
        )).thenReturn(Optional.of(record));
        when(paymentRepository.findById(20)).thenReturn(Optional.of(payment));

        PaymentResponse response =
                paymentService.payOrder(10, request, "payment-key");

        assertThat(response.getId()).isEqualTo(20);
        assertThat(response.getOrderId()).isEqualTo(10);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);

        verify(orderRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void throwsWhenIdempotencyRecordReferencesMissingPayment() {
        IdempotencyRecord record = new IdempotencyRecord(
                "payment-key",
                "/api/orders/10/payments",
                999
        );

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath(
                "payment-key",
                "/api/orders/10/payments"
        )).thenReturn(Optional.of(record));
        when(paymentRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> paymentService.payOrder(10, request, "payment-key")
        ).isInstanceOf(PaymentNotFoundException.class);

        verify(orderRepository, never()).findById(any());
    }

    @Test
    void throwsWhenOrderDoesNotExist() {
        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath(
                "payment-key",
                "/api/orders/10/payments"
        )).thenReturn(Optional.empty());
        when(orderRepository.findById(10)).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> paymentService.payOrder(10, request, "payment-key")
        ).isInstanceOf(OrderNotFoundException.class);

        verify(paymentRepository, never()).existsByOrder(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void rejectsAlreadyPaidOrderBeforeCheckingExistingPayment() {
        Order order = orderWithStatus(10, OrderStatus.PAID);

        prepareNewPaymentAttempt(order);

        assertThatThrownBy(
                () -> paymentService.payOrder(10, request, "payment-key")
        ).isInstanceOf(OrderAlreadyPaidException.class);

        verify(paymentRepository, never()).existsByOrder(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void rejectsOrderWhoseStatusIsNotPending() {
        Order order = orderWithStatus(10, OrderStatus.CANCELLED);

        prepareNewPaymentAttempt(order);

        assertThatThrownBy(
                () -> paymentService.payOrder(10, request, "payment-key")
        ).isInstanceOf(InvalidOrderStatusException.class)
                .hasMessageContaining("Only PENDING orders can be paid")
                .hasMessageContaining("CANCELLED");

        verify(paymentRepository, never()).existsByOrder(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void rejectsPendingOrderWhenPaymentAlreadyExists() {
        Order order = pendingOrder(10);

        prepareNewPaymentAttempt(order);
        when(paymentRepository.existsByOrder(order)).thenReturn(true);

        assertThatThrownBy(
                () -> paymentService.payOrder(10, request, "payment-key")
        ).isInstanceOf(OrderAlreadyPaidException.class);

        verify(paymentRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void createsPaymentRecordIdempotencyRecordOrderUpdateAndOutboxEvent() {
        Order order = pendingOrder(10);

        prepareNewPaymentAttempt(order);
        when(paymentRepository.existsByOrder(order)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(20);
            return payment;
        });

        PaymentResponse response =
                paymentService.payOrder(10, request, "payment-key");

        assertThat(response.getId()).isEqualTo(20);
        assertThat(response.getOrderId()).isEqualTo(10);
        assertThat(response.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(response.getPaidAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

        verify(idempotencyRecordRepository).save(
                any(IdempotencyRecord.class)
        );
        verify(orderRepository).save(order);
        verify(outboxEventService).saveEvent(
                eq("PAYMENT"),
                eq("20"),
                eq("PAYMENT_PAID"),
                eq("payment-paid"),
                any()
        );
    }

    @Test
    void getPaymentThrowsWhenOrderDoesNotExist() {
        when(orderRepository.findById(10)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByOrder(10))
                .isInstanceOf(OrderNotFoundException.class);

        verify(paymentRepository, never()).findByOrder(any());
    }

    @Test
    void getPaymentThrowsWhenOrderHasNoPayment() {
        Order order = pendingOrder(10);

        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByOrder(10))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void getPaymentReturnsExistingPayment() {
        Order order = orderWithStatus(10, OrderStatus.PAID);
        Payment payment = paidPayment(20, order);

        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByOrder(10);

        assertThat(response.getId()).isEqualTo(20);
        assertThat(response.getOrderId()).isEqualTo(10);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    private void prepareNewPaymentAttempt(Order order) {
        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath(
                "payment-key",
                "/api/orders/10/payments"
        )).thenReturn(Optional.empty());
        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
    }

    private Order pendingOrder(Integer id) {
        return orderWithStatus(id, OrderStatus.PENDING);
    }

    private Order orderWithStatus(Integer id, OrderStatus status) {
        Order order = new Order(null, new BigDecimal("1000.00"), status);
        order.setId(id);
        return order;
    }

    private Payment paidPayment(Integer id, Order order) {
        Payment payment = new Payment(
                order,
                order.getTotalAmount(),
                PaymentStatus.PAID,
                PaymentMethod.CREDIT_CARD
        );
        payment.setId(id);
        payment.setPaidAt(LocalDateTime.now());
        return payment;
    }
}
