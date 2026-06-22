package com.ravan.SpringBootLab.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EventRetentionCleanupService {

    private static final Logger logger =
            LoggerFactory.getLogger(EventRetentionCleanupService.class);

    private final JdbcTemplate jdbcTemplate;
    private final long processedEventsRetentionDays;
    private final long orderEventAuditRetentionDays;

    public EventRetentionCleanupService(
            JdbcTemplate jdbcTemplate,
            @Value("${event.retention.processed-events-days:30}")
            long processedEventsRetentionDays,
            @Value("${event.retention.order-event-audit-days:90}")
            long orderEventAuditRetentionDays
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.processedEventsRetentionDays = processedEventsRetentionDays;
        this.orderEventAuditRetentionDays = orderEventAuditRetentionDays;
    }

    @Scheduled(
            fixedDelayString = "${event.retention.cleanup.fixed-delay-ms:86400000}",
            initialDelayString = "${event.retention.cleanup.initial-delay-ms:86400000}"
    )
    public void scheduledCleanup() {
        CleanupResult result = deleteExpiredRecords();

        if (result.processedEventsDeleted() > 0
                || result.orderEventAuditsDeleted() > 0) {
            logger.info(
                    "Event retention cleanup completed: processedEventsDeleted={}, orderEventAuditsDeleted={}",
                    result.processedEventsDeleted(),
                    result.orderEventAuditsDeleted()
            );
        }
    }

    @Transactional
    public CleanupResult deleteExpiredRecords() {
        LocalDateTime processedEventsCutoff = LocalDateTime.now()
                .minusDays(processedEventsRetentionDays);

        LocalDateTime orderEventAuditCutoff = LocalDateTime.now()
                .minusDays(orderEventAuditRetentionDays);

        int processedEventsDeleted = jdbcTemplate.update(
                """
                DELETE FROM processed_events
                WHERE processed_at < ?
                """,
                processedEventsCutoff
        );

        int orderEventAuditsDeleted = jdbcTemplate.update(
                """
                DELETE FROM order_event_audit
                WHERE created_at < ?
                """,
                orderEventAuditCutoff
        );

        return new CleanupResult(
                processedEventsDeleted,
                orderEventAuditsDeleted
        );
    }

    public record CleanupResult(
            int processedEventsDeleted,
            int orderEventAuditsDeleted
    ) {
    }
}
