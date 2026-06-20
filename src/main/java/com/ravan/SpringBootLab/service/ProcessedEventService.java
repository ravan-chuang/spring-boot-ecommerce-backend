package com.ravan.SpringBootLab.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProcessedEventService {

    private static final Logger logger =
            LoggerFactory.getLogger(ProcessedEventService.class);

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean processIfFirstTime(
            UUID eventId,
            String consumerName,
            Runnable businessAction
    ) {
        int insertedRows = jdbcTemplate.update(
                """
                INSERT INTO processed_events (
                    event_id,
                    consumer_name,
                    processed_at
                )
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (event_id, consumer_name) DO NOTHING
                """,
                eventId,
                consumerName
        );

        if (insertedRows == 0) {
            logger.info(
                    "Skipped duplicate event: eventId={}, consumer={}",
                    eventId,
                    consumerName
            );
            return false;
        }

        businessAction.run();

        logger.info(
                "Processed event for the first time: eventId={}, consumer={}",
                eventId,
                consumerName
        );

        return true;
    }
}
