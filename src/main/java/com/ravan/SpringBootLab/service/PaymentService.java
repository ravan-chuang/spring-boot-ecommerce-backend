package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.ravan.SpringBootLab.event.PaymentPaidEvent;
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
import com.ravan.SpringBootLab.model.PaymentStatus;
import com.ravan.SpringBootLab.repository.IdempotencyRecordRepository;
import com.ravan.SpringBootLab.repository.OrderRepository;
import com.ravan.SpringBootLab.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OutboxEventService outboxEventService;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            OutboxEventService outboxEventService
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public PaymentResponse payOrder(
        Integer orderId, 
        CreatePaymentRequest request,
        String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException();
        }
        
        String requestPath = "/api/orders/" + orderId + "/payments";
        
        IdempotencyRecord existingRecord = idempotencyRecordRepository
        .findByIdempotencyKeyAndRequestPath(idempotencyKey, requestPath)
        .orElse(null);
        
        if (existingRecord != null) {
            Payment existingPayment = paymentRepository.findById(existingRecord.getPaymentId())
            .orElseThrow(() -> new PaymentNotFoundException(orderId));
            
            return toPaymentResponse(existingPayment);
        }
        
        Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.PAID) {
            throw new OrderAlreadyPaidException(orderId);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStatusException(
                    "Only PENDING orders can be paid. Current status: " + order.getStatus()
            );
        }

        if (paymentRepository.existsByOrder(order)) {
            throw new OrderAlreadyPaidException(orderId);
        }

        Payment payment = new Payment(
                order,
                order.getTotalAmount(),
                PaymentStatus.PAID,
                request.getMethod()
        );

        payment.setPaidAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        IdempotencyRecord record = new IdempotencyRecord(
            idempotencyKey,
            requestPath,
            savedPayment.getId()
        );
        
        idempotencyRecordRepository.save(record);
        
        order.setStatus(OrderStatus.PAID);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        outboxEventService.saveEvent(
                "PAYMENT",
                String.valueOf(savedPayment.getId()),
                "PAYMENT_PAID",
                KafkaTopicConfig.PAYMENT_PAID_TOPIC,
                new PaymentPaidEvent(
                        savedPayment.getId(),
                        order.getId(),
                        savedPayment.getAmount(),
                        savedPayment.getMethod().name(),
                        savedPayment.getPaidAt()
                )
        );

        return toPaymentResponse(savedPayment);
    }

    public PaymentResponse getPaymentByOrder(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        return toPaymentResponse(payment);
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}