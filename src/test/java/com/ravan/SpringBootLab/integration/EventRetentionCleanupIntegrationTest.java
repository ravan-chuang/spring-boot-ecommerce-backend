package com.ravan.SpringBootLab.integration;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.service.EventRetentionCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.publisher.enabled=false",
        "event.retention.processed-events-days=30",
        "event.retention.order-event-audit-days=90",
        "event.retention.cleanup.initial-delay-ms=999999999",
        "event.retention.cleanup.fixed-delay-ms=999999999"
})
class EventRetentionCleanupIntegrationTest
        extends TestcontainersIntegrationTest {

    @Autowired
    private EventRetentionCleanupService eventRetentionCleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearEventTables() {
        jdbcTemplate.update("DELETE FROM order_event_audit");
        jdbcTemplate.update("DELETE FROM processed_events");
    }

    @Test
    void shouldDeleteOnlyExpiredEventRecords() {
        UUID oldProcessedEventId = UUID.randomUUID();
        UUID recentProcessedEventId = UUID.randomUUID();
        UUID oldAuditEventId = UUID.randomUUID();
        UUID recentAuditEventId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO processed_events (
                    event_id,
                    consumer_name,
                    processed_at
                )
                VALUES (?, ?, ?)
                """,
                oldProcessedEventId,
                "order-created-consumer",
                LocalDateTime.now().minusDays(31)
        );

        jdbcTemplate.update(
                """
                INSERT INTO processed_events (
                    event_id,
                    consumer_name,
                    processed_at
                )
                VALUES (?, ?, ?)
                """,
                recentProcessedEventId,
                "order-created-consumer",
                LocalDateTime.now().minusDays(1)
        );

        jdbcTemplate.update(
                """
                INSERT INTO order_event_audit (
                    event_id,
                    event_type,
                    payload,
                    created_at
                )
                VALUES (?, ?, ?, ?)
                """,
                oldAuditEventId,
                "ORDER_CREATED",
                "{\"orderId\": 1}",
                LocalDateTime.now().minusDays(91)
        );

        jdbcTemplate.update(
                """
                INSERT INTO order_event_audit (
                    event_id,
                    event_type,
                    payload,
                    created_at
                )
                VALUES (?, ?, ?, ?)
                """,
                recentAuditEventId,
                "ORDER_CREATED",
                "{\"orderId\": 2}",
                LocalDateTime.now().minusDays(1)
        );

        EventRetentionCleanupService.CleanupResult result =
                eventRetentionCleanupService.deleteExpiredRecords();

        Integer processedEventsRemaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events",
                Integer.class
        );

        Integer auditRecordsRemaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_event_audit",
                Integer.class
        );

        assertEquals(1, result.processedEventsDeleted());
        assertEquals(1, result.orderEventAuditsDeleted());
        assertEquals(1, processedEventsRemaining);
        assertEquals(1, auditRecordsRemaining);
    }
}
