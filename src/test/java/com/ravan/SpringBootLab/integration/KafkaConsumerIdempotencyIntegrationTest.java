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
    void clearEventState() {
        jdbcTemplate.update("DELETE FROM order_event_audit");
        jdbcTemplate.update("DELETE FROM processed_events");
    }

    @Test
    void shouldCreateOnlyOneAuditRecordForDuplicateKafkaEvent()
            throws Exception {
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

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM order_event_audit
                WHERE event_id = ?
                """,
                Integer.class,
                eventId
        );

        assertEquals(1, processedCount);
        assertEquals(1, auditCount);
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

            Integer auditCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM order_event_audit
                    WHERE event_id = ?
                    """,
                    Integer.class,
                    eventId
            );

            if (processedCount != null
                    && processedCount == 1
                    && auditCount != null
                    && auditCount == 1) {
                return;
            }

            Thread.sleep(200);
        }

        throw new AssertionError(
                "Timed out waiting for Kafka consumer side effect: "
                        + eventId
        );
    }
}
