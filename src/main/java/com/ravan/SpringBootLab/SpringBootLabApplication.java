package com.ravan.SpringBootLab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableKafkaRetryTopic
@EnableCaching
@EnableScheduling
@SpringBootApplication
public class SpringBootLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootLabApplication.class, args);
    }
}
