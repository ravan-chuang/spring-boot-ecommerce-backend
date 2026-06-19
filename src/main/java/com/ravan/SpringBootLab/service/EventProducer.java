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

    public void send(String topic, String key, String payload) {
        try {
            SendResult<String, String> result = kafkaTemplate.send(
                    topic,
                    key,
                    payload
            ).get();

            kafkaTemplate.flush();

            logger.info(
                    "Sent outbox event: topic={}, key={}, partition={}, offset={}",
                    result.getRecordMetadata().topic(),
                    key,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sending Kafka event", exception);
        } catch (ExecutionException exception) {
            throw new RuntimeException("Failed to send Kafka event", exception);
        }
    }

    public void sendOrderCreatedEvent(OrderCreatedEvent event) {
        try {
            send(
                    KafkaTopicConfig.ORDER_CREATED_TOPIC,
                    String.valueOf(event.getOrderId()),
                    objectMapper.writeValueAsString(event)
            );
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Failed to serialize OrderCreatedEvent", exception);
        }
    }

    public void sendPaymentPaidEvent(PaymentPaidEvent event) {
        try {
            send(
                    KafkaTopicConfig.PAYMENT_PAID_TOPIC,
                    String.valueOf(event.getPaymentId()),
                    objectMapper.writeValueAsString(event)
            );
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Failed to serialize PaymentPaidEvent", exception);
        }
    }
}
