package com.ravan.SpringBootLab.integration;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.ravan.SpringBootLab.service.EventProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "outbox.publisher.enabled=false"
})
class KafkaConsumerIdempotencyIntegrationTest
        extends TestcontainersIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearProcessedEvents() {
        jdbcTemplate.update("DELETE FROM processed_events");
    }

    @Test
    void shouldProcessDuplicateKafkaEventOnlyOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        String consumerName = "order-created-consumer";
        String key = "idempotency-test-" + eventId;
        String payload = """
                {
                  "orderId": 99999,
                  "userId": 1,
                  "totalAmount": 1000
                }
                """;

        sendOrderCreatedEvent(eventId, key, payload);

        waitUntilProcessed(eventId, consumerName, 15_000);

        sendOrderCreatedEvent(eventId, key, payload);

        Thread.sleep(1_500);

        Integer processedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_events
                WHERE event_id = ?
                  AND consumer_name = ?
                """,
                Integer.class,
                eventId,
                consumerName
        );

        assertEquals(1, processedCount);
    }

    private void sendOrderCreatedEvent(
            UUID eventId,
            String key,
            String payload
    ) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaTopicConfig.ORDER_CREATED_TOPIC,
                key,
                payload
        );

        record.headers().add(
                EventProducer.OUTBOX_EVENT_ID_HEADER,
                eventId.toString().getBytes(StandardCharsets.UTF_8)
        );

        kafkaTemplate.send(record).get();
        kafkaTemplate.flush();
    }

    private void waitUntilProcessed(
            UUID eventId,
            String consumerName,
            long timeoutMillis
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < deadline) {
            Integer processedCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM processed_events
                    WHERE event_id = ?
                      AND consumer_name = ?
                    """,
                    Integer.class,
                    eventId,
                    consumerName
            );

            if (processedCount != null && processedCount == 1) {
                return;
            }

            Thread.sleep(200);
        }

        throw new AssertionError(
                "Timed out waiting for Kafka consumer to process event: "
                        + eventId
        );
    }
}
