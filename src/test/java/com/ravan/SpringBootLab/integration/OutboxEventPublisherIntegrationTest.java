package com.ravan.SpringBootLab.integration;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import com.ravan.SpringBootLab.service.OutboxEventPublisher;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(properties = {
        "outbox.publisher.enabled=true",
        "outbox.publisher.initial-delay-ms=60000",
        "outbox.publisher.fixed-delay-ms=60000",
        "spring.kafka.listener.auto-startup=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxEventPublisherIntegrationTest extends TestcontainersIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    void shouldPublishPendingOutboxEventAndMarkItPublished() {
        String eventKey = "outbox-test-" + UUID.randomUUID();
        String payload = "{\"event\":\"outbox-test\"}";

        OutboxEvent event = new OutboxEvent(
                "ORDER",
                eventKey,
                "ORDER_CREATED",
                KafkaTopicConfig.ORDER_CREATED_TOPIC,
                payload
        );

        OutboxEvent savedEvent = outboxEventRepository.saveAndFlush(event);

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(consumerProperties())) {

            consumer.subscribe(List.of(KafkaTopicConfig.ORDER_CREATED_TOPIC));

            // Let the consumer join its group before the event is published.
            consumer.poll(Duration.ofSeconds(1));

            outboxEventPublisher.publishPendingEvents();

            OutboxEvent publishedEvent = outboxEventRepository
                    .findById(savedEvent.getId())
                    .orElseThrow();

            assertEquals(OutboxEventStatus.PUBLISHED, publishedEvent.getStatus());
            assertNotNull(publishedEvent.getPublishedAt());

            assertKafkaReceivedEvent(consumer, eventKey, payload);
        }
    }

    private void assertKafkaReceivedEvent(
            KafkaConsumer<String, String> consumer,
            String expectedKey,
            String expectedPayload
    ) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record :
                    consumer.poll(Duration.ofMillis(500))) {

                if (expectedKey.equals(record.key())
                        && expectedPayload.equals(record.value())) {
                    return;
                }
            }
        }

        fail("Expected the outbox event to be published to Kafka.");
    }

    private Properties consumerProperties() {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "outbox-verification-" + UUID.randomUUID()
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        return properties;
    }
}
