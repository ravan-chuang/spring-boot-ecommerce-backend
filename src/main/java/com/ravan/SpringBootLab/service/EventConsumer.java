package com.ravan.SpringBootLab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.ravan.SpringBootLab.event.OrderCreatedEvent;
import com.ravan.SpringBootLab.event.PaymentPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class EventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(EventConsumer.class);

    private final ObjectMapper objectMapper;

    public EventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.ORDER_CREATED_TOPIC,
            groupId = "order-created-consumer"
    )
    public void handleOrderCreatedEvent(String message) {
        logger.info("Received raw OrderCreatedEvent message: {}", message);

        try {
            OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);

            logger.info(
                    "Consumed OrderCreatedEvent: orderId={}, userId={}, totalAmount={}",
                    event.getOrderId(),
                    event.getUserId(),
                    event.getTotalAmount()
            );
        } catch (JsonProcessingException e) {
            logger.error("Failed to consume OrderCreatedEvent. Raw message={}", message, e);
            throw new RuntimeException("Failed to consume OrderCreatedEvent", e);
        }
    }

    @KafkaListener(
            topics = KafkaTopicConfig.PAYMENT_PAID_TOPIC,
            groupId = "payment-paid-consumer"
    )
    public void handlePaymentPaidEvent(String message) {
        logger.info("Received raw PaymentPaidEvent message: {}", message);

        try {
            PaymentPaidEvent event = objectMapper.readValue(message, PaymentPaidEvent.class);

            logger.info(
                    "Consumed PaymentPaidEvent: paymentId={}, orderId={}, amount={}, method={}",
                    event.getPaymentId(),
                    event.getOrderId(),
                    event.getAmount(),
                    event.getMethod()
            );
        } catch (JsonProcessingException e) {
            logger.error("Failed to consume PaymentPaidEvent. Raw message={}", message, e);
            throw new RuntimeException("Failed to consume PaymentPaidEvent", e);
        }
    }
}