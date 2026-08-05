package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.CreatePaymentRequest;
import com.ravan.SpringBootLab.dto.PaymentResponse;
import com.ravan.SpringBootLab.event.PaymentPaidEvent;
import com.ravan.SpringBootLab.exception.IdempotencyConflictException;
import com.ravan.SpringBootLab.exception.IdempotencyKeyRequiredException;
import com.ravan.SpringBootLab.exception.InvalidIdempotencyKeyException;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String CREDIT_CARD_FINGERPRINT =
            "b41381f93987bd40ee50d3325112ba45be62e4cd0999e1bf0c866881f4e2c0a4";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private OutboxEventService outboxEventService;

    private PaymentService paymentService;
    private Order order;
    private Payment payment;
    private CreatePaymentRequest request;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                idempotencyRecordRepository,
                outboxEventService,
                24
        );

        order = new Order(null, new BigDecimal("1500.00"), OrderStatus.PENDING);
        order.setId(10);

        payment = new Payment(
                order,
                new BigDecimal("1500.00"),
                PaymentStatus.PAID,
                PaymentMethod.CREDIT_CARD
        );
        payment.setId(20);
        payment.setPaidAt(LocalDateTime.of(2026, 8, 5, 0, 10));
        payment.setCreatedAt(LocalDateTime.of(2026, 8, 5, 0, 10));
        payment.setUpdatedAt(LocalDateTime.of(2026, 8, 5, 0, 10));

        request = mock(CreatePaymentRequest.class);
        lenient().when(request.getMethod()).thenReturn(PaymentMethod.CREDIT_CARD);
    }

    @Test
    void payOrderRejectsNullIdempotencyKey() {
        assertThrows(
                IdempotencyKeyRequiredException.class,
                () -> paymentService.payOrder(10, request, null)
        );

        verifyNoInteractions(
                paymentRepository,
                orderRepository,
                idempotencyRecordRepository,
                outboxEventService
        );
    }

    @Test
    void payOrderRejectsBlankIdempotencyKey() {
        assertThrows(
                IdempotencyKeyRequiredException.class,
                () -> paymentService.payOrder(10, request, "   ")
        );

        verifyNoInteractions(
                paymentRepository,
                orderRepository,
                idempotencyRecordRepository,
                outboxEventService
        );
    }

    @Test
    void payOrderRejectsIdempotencyKeyLongerThanDatabaseLimit() {
        assertThrows(
                InvalidIdempotencyKeyException.class,
                () -> paymentService.payOrder(10, request, "k".repeat(256))
        );

        verifyNoInteractions(
                paymentRepository,
                orderRepository,
                idempotencyRecordRepository,
                outboxEventService
        );
    }

    @Test
    void payOrderReturnsExistingPaymentFromFirstIdempotencyLookup() {
        String path = "/api/orders/10/payments";
        IdempotencyRecord record = idempotencyRecord("key-1", path, CREDIT_CARD_FINGERPRINT, 20);

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath("key-1", path))
                .thenReturn(Optional.of(record));
        when(paymentRepository.findById(20)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.payOrder(10, request, "key-1");

        assertEquals(20, response.getId());
        assertEquals(10, response.getOrderId());
        assertEquals(PaymentStatus.PAID, response.getStatus());
        verify(paymentRepository).findById(20);
        verifyNoInteractions(orderRepository, outboxEventService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void payOrderRejectsReusedKeyWithDifferentPayload() {
        String path = "/api/orders/10/payments";
        IdempotencyRecord record = idempotencyRecord(
                "key-conflict",
                path,
                "f1c2d1c9590efedb31bdb7c66bc2011bfbf904838580d9e3852fc6f33f8065ff",
                20
        );

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath(
                "key-conflict",
                path
        )).thenReturn(Optional.of(record));

        assertThrows(
                IdempotencyConflictException.class,
                () -> paymentService.payOrder(10, request, "key-conflict")
        );

        verifyNoInteractions(paymentRepository, orderRepository, outboxEventService);
    }

    @Test
    void payOrderThrowsWhenIdempotencyRecordReferencesMissingPayment() {
        String path = "/api/orders/10/payments";
        IdempotencyRecord record = idempotencyRecord("key-1", path, CREDIT_CARD_FINGERPRINT, 999);

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath("key-1", path))
                .thenReturn(Optional.of(record));
        when(paymentRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.payOrder(10, request, "key-1")
        );

        verifyNoInteractions(orderRepository, outboxEventService);
    }

    @Test
    void payOrderReturnsExistingPaymentFromSecondLookupAfterLock() {
        String path = "/api/orders/10/payments";
        IdempotencyRecord record = idempotencyRecord("key-2", path, CREDIT_CARD_FINGERPRINT, 20);

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath("key-2", path))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(record));
        when(orderRepository.findByIdForUpdate(10)).thenReturn(Optional.of(order));
        when(paymentRepository.findById(20)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.payOrder(10, request, "key-2");

        assertEquals(20, response.getId());
        verify(orderRepository).findByIdForUpdate(10);
        verify(paymentRepository).findById(20);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void payOrderThrowsWhenOrderDoesNotExist() {
        String path = "/api/orders/404/payments";

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath("key-3", path))
                .thenReturn(Optional.empty());
        when(orderRepository.findByIdForUpdate(404)).thenReturn(Optional.empty());

        assertThrows(
                OrderNotFoundException.class,
                () -> paymentService.payOrder(404, request, "key-3")
        );

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void payOrderRejectsAlreadyPaidOrder() {
        String path = "/api/orders/10/payments";
        order.setStatus(OrderStatus.PAID);

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath("key-4", path))
                .thenReturn(Optional.empty());
        when(orderRepository.findByIdForUpdate(10)).thenReturn(Optional.of(order));

        assertThrows(
                OrderAlreadyPaidException.class,
                () -> paymentService.payOrder(10, request, "key-4")
        );

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void payOrderRejectsNonPendingOrder() {
        String path = "/api/orders/10/payments";
        order.setStatus(OrderStatus.CANCELLED);

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath("key-5", path))
                .thenReturn(Optional.empty());
        when(orderRepository.findByIdForUpdate(10)).thenReturn(Optional.of(order));

        assertThrows(
                InvalidOrderStatusException.class,
                () -> paymentService.payOrder(10, request, "key-5")
        );

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void payOrderRejectsWhenPaymentAlreadyExistsForOrder() {
        String path = "/api/orders/10/payments";

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath("key-6", path))
                .thenReturn(Optional.empty());
        when(orderRepository.findByIdForUpdate(10)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrder(order)).thenReturn(true);

        assertThrows(
                OrderAlreadyPaidException.class,
                () -> paymentService.payOrder(10, request, "key-6")
        );

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxEventService);
    }

    @Test
    void payOrderPersistsPaymentIdempotencyRecordOrderAndOutboxEvent() {
        String path = "/api/orders/10/payments";

        when(idempotencyRecordRepository.findByIdempotencyKeyAndRequestPath("key-7", path))
                .thenReturn(Optional.empty());
        when(orderRepository.findByIdForUpdate(10)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrder(order)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setId(20);
            return saved;
        });

        PaymentResponse response = paymentService.payOrder(10, request, "key-7");

        assertEquals(20, response.getId());
        assertEquals(10, response.getOrderId());
        assertEquals(new BigDecimal("1500.00"), response.getAmount());
        assertEquals(PaymentMethod.CREDIT_CARD, response.getMethod());
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertNotNull(order.getUpdatedAt());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertSame(order, saved.getOrder());
        assertEquals(PaymentStatus.PAID, saved.getStatus());
        assertNotNull(saved.getPaidAt());

        ArgumentCaptor<IdempotencyRecord> recordCaptor =
                ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordRepository).save(recordCaptor.capture());
        IdempotencyRecord savedRecord = recordCaptor.getValue();
        assertEquals("key-7", savedRecord.getIdempotencyKey());
        assertEquals(path, savedRecord.getRequestPath());
        assertEquals(CREDIT_CARD_FINGERPRINT, savedRecord.getRequestFingerprint());
        assertEquals(20, savedRecord.getPaymentId());
        assertEquals(200, savedRecord.getResponseStatus());
        assertNotNull(savedRecord.getExpiresAt());
        assertTrue(savedRecord.getExpiresAt().isAfter(LocalDateTime.now().plusHours(23)));

        verify(orderRepository).save(order);
        verify(outboxEventService).saveEvent(
                eq("PAYMENT"),
                eq("20"),
                eq("PAYMENT_PAID"),
                anyString(),
                any(PaymentPaidEvent.class)
        );
    }

    @Test
    void getPaymentByOrderReturnsMappedPayment() {
        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByOrder(10);

        assertEquals(20, response.getId());
        assertEquals(10, response.getOrderId());
        assertEquals(PaymentStatus.PAID, response.getStatus());
        verify(orderRepository).findById(10);
        verify(paymentRepository).findByOrder(order);
    }

    @Test
    void getPaymentByOrderThrowsWhenOrderMissing() {
        when(orderRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(
                OrderNotFoundException.class,
                () -> paymentService.getPaymentByOrder(404)
        );

        verify(paymentRepository, never()).findByOrder(any());
    }

    @Test
    void getPaymentByOrderThrowsWhenPaymentMissing() {
        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.empty());

        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.getPaymentByOrder(10)
        );
    }

    private IdempotencyRecord idempotencyRecord(
            String key,
            String path,
            String fingerprint,
            Integer paymentId
    ) {
        return new IdempotencyRecord(
                key,
                path,
                fingerprint,
                paymentId,
                200,
                LocalDateTime.now().plusHours(24)
        );
    }
}
