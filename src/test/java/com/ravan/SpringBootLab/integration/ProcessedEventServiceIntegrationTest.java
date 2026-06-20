package com.ravan.SpringBootLab.integration;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.service.ProcessedEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.publisher.enabled=false"
})
class ProcessedEventServiceIntegrationTest
        extends TestcontainersIntegrationTest {

    @Autowired
    private ProcessedEventService processedEventService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearProcessedEvents() {
        jdbcTemplate.update("DELETE FROM processed_events");
    }

    @Test
    void shouldProcessSameEventOnlyOnceForSameConsumer() {
        UUID eventId = UUID.randomUUID();
        String consumerName = "order-created-consumer";
        AtomicInteger businessActionCount = new AtomicInteger();

        boolean firstAttempt = processedEventService.processIfFirstTime(
                eventId,
                consumerName,
                businessActionCount::incrementAndGet
        );

        boolean duplicateAttempt = processedEventService.processIfFirstTime(
                eventId,
                consumerName,
                businessActionCount::incrementAndGet
        );

        Integer storedCount = countProcessedEvents(eventId, consumerName);

        assertTrue(firstAttempt);
        assertFalse(duplicateAttempt);
        assertEquals(1, businessActionCount.get());
        assertEquals(1, storedCount);
    }

    @Test
    void shouldRollbackProcessedMarkerWhenBusinessActionFails() {
        UUID eventId = UUID.randomUUID();
        String consumerName = "payment-paid-consumer";
        AtomicInteger businessActionCount = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () -> processedEventService.processIfFirstTime(
                        eventId,
                        consumerName,
                        () -> {
                            businessActionCount.incrementAndGet();
                            throw new IllegalStateException(
                                    "Simulated consumer processing failure"
                            );
                        }
                )
        );

        assertEquals(
                0,
                countProcessedEvents(eventId, consumerName)
        );

        boolean retryAttempt = processedEventService.processIfFirstTime(
                eventId,
                consumerName,
                businessActionCount::incrementAndGet
        );

        assertTrue(retryAttempt);
        assertEquals(2, businessActionCount.get());
        assertEquals(
                1,
                countProcessedEvents(eventId, consumerName)
        );
    }

    private Integer countProcessedEvents(
            UUID eventId,
            String consumerName
    ) {
        return jdbcTemplate.queryForObject(
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
    }
}
