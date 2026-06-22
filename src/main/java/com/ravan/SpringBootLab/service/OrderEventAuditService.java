package com.ravan.SpringBootLab.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderEventAuditService {

    private static final Logger logger =
            LoggerFactory.getLogger(OrderEventAuditService.class);

    private final JdbcTemplate jdbcTemplate;

    public OrderEventAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordOrderCreatedEvent(
            UUID eventId,
            String payload
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO order_event_audit (
                    event_id,
                    event_type,
                    payload,
                    created_at
                )
                VALUES (?, 'ORDER_CREATED', ?, CURRENT_TIMESTAMP)
                ON CONFLICT (event_id) DO NOTHING
                """,
                eventId,
                payload
        );

        logger.info(
                "Recorded order event audit: eventId={}, eventType=ORDER_CREATED",
                eventId
        );
    }
}
