package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.stereotype.Component;

@Component
public class EventConsumer {

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(
                    delay = 1000,
                    multiplier = 2.0,
                    maxDelay = 4000
            ),
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = KafkaTopicConfig.ORDER_CREATED_TOPIC,
            groupId = "order-created-consumer"
    )
    public void handleOrderCreatedEvent(String message) {
        System.out.println("Received raw OrderCreatedEvent message: " + message);

        processOrderCreatedEvent(message);
    }

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(
                    delay = 1000,
                    multiplier = 2.0,
                    maxDelay = 4000
            ),
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = KafkaTopicConfig.PAYMENT_PAID_TOPIC,
            groupId = "payment-paid-consumer"
    )
    public void handlePaymentPaidEvent(String message) {
        System.out.println("Received raw PaymentPaidEvent message: " + message);

        processPaymentPaidEvent(message);
    }

    @DltHandler
    public void handleDeadLetterMessage(
            ConsumerRecord<String, String> record
    ) {
        System.err.println(
                "Message moved to DLT: topic=" + record.topic()
                        + ", partition=" + record.partition()
                        + ", offset=" + record.offset()
                        + ", key=" + record.key()
                        + ", value=" + record.value()
        );
    }

    private void processOrderCreatedEvent(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("OrderCreatedEvent message is empty");
        }

        System.out.println("Consumed OrderCreatedEvent: " + message);
    }

    private void processPaymentPaidEvent(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("PaymentPaidEvent message is empty");
        }

        System.out.println("Consumed PaymentPaidEvent: " + message);
    }
}