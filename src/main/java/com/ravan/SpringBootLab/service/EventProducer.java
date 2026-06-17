package com.ravan.SpringBootLab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.ravan.SpringBootLab.event.OrderCreatedEvent;
import com.ravan.SpringBootLab.event.PaymentPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
public class EventProducer {

    private static final Logger logger = LoggerFactory.getLogger(EventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendOrderCreatedEvent(OrderCreatedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);

            SendResult<String, String> result = kafkaTemplate.send(
                    KafkaTopicConfig.ORDER_CREATED_TOPIC,
                    String.valueOf(event.getOrderId()),
                    message
            ).get();

            kafkaTemplate.flush();

            logger.info(
                    "Sent OrderCreatedEvent: orderId={}, topic={}, partition={}, offset={}",
                    event.getOrderId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize OrderCreatedEvent: orderId={}", event.getOrderId(), e);
            throw new RuntimeException("Failed to serialize OrderCreatedEvent", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while sending OrderCreatedEvent: orderId={}", event.getOrderId(), e);
            throw new RuntimeException("Interrupted while sending OrderCreatedEvent", e);
        } catch (ExecutionException e) {
            logger.error("Failed to send OrderCreatedEvent to Kafka: orderId={}", event.getOrderId(), e);
            throw new RuntimeException("Failed to send OrderCreatedEvent to Kafka", e);
        }
    }

    public void sendPaymentPaidEvent(PaymentPaidEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);

            SendResult<String, String> result = kafkaTemplate.send(
                    KafkaTopicConfig.PAYMENT_PAID_TOPIC,
                    String.valueOf(event.getPaymentId()),
                    message
            ).get();

            kafkaTemplate.flush();

            logger.info(
                    "Sent PaymentPaidEvent: paymentId={}, orderId={}, topic={}, partition={}, offset={}",
                    event.getPaymentId(),
                    event.getOrderId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize PaymentPaidEvent: paymentId={}", event.getPaymentId(), e);
            throw new RuntimeException("Failed to serialize PaymentPaidEvent", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while sending PaymentPaidEvent: paymentId={}", event.getPaymentId(), e);
            throw new RuntimeException("Interrupted while sending PaymentPaidEvent", e);
        } catch (ExecutionException e) {
            logger.error("Failed to send PaymentPaidEvent to Kafka: paymentId={}", event.getPaymentId(), e);
            throw new RuntimeException("Failed to send PaymentPaidEvent to Kafka", e);
        }
    }
}