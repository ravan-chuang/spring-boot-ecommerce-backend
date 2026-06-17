package com.ravan.SpringBootLab.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_CREATED_TOPIC = "order-created";
    public static final String PAYMENT_PAID_TOPIC = "payment-paid";

    @Bean
    public NewTopic orderCreatedTopic() {
        return new NewTopic(ORDER_CREATED_TOPIC, 1, (short) 1);
    }

    @Bean
    public NewTopic paymentPaidTopic() {
        return new NewTopic(PAYMENT_PAID_TOPIC, 1, (short) 1);
    }
}