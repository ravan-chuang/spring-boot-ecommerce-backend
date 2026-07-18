package com.ravan.SpringBootLab.integration;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import com.ravan.SpringBootLab.service.OutboxEventClaimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.publisher.enabled=false"
})
class OutboxEventClaimServiceIntegrationTest
        extends TestcontainersIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventClaimService outboxEventClaimService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearOutboxEvents() {
        outboxEventRepository.deleteAllInBatch();
    }

    @Test
    void shouldAllowOnlyOneWorkerToClaimTheSamePendingEvent() {
        OutboxEvent savedEvent = createPendingEvent();

        List<UUID> workerAClaims =
                outboxEventClaimService.claimPendingEvents(10, "worker-a");

        List<UUID> workerBClaims =
                outboxEventClaimService.claimPendingEvents(10, "worker-b");

        assertEquals(List.of(savedEvent.getId()), workerAClaims);
        assertTrue(workerBClaims.isEmpty());

        OutboxEvent claimedEvent = outboxEventRepository
                .findById(savedEvent.getId())
                .orElseThrow();

        assertEquals(OutboxEventStatus.PROCESSING, claimedEvent.getStatus());
        assertEquals("worker-a", claimedEvent.getProcessingBy());
    }


    @Test
    void shouldDistributePendingEventsAcrossConcurrentWorkersWithoutOverlap()
            throws Exception {
        int eventCount = 20;
        for (int index = 0; index < eventCount; index++) {
            createPendingEvent();
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<List<UUID>> workerA = executor.submit(() -> {
                ready.countDown();
                start.await();
                return outboxEventClaimService.claimPendingEvents(10, "worker-a");
            });
            Future<List<UUID>> workerB = executor.submit(() -> {
                ready.countDown();
                start.await();
                return outboxEventClaimService.claimPendingEvents(10, "worker-b");
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<UUID> workerAClaims = workerA.get(10, TimeUnit.SECONDS);
            List<UUID> workerBClaims = workerB.get(10, TimeUnit.SECONDS);

            Set<UUID> overlap = new HashSet<>(workerAClaims);
            overlap.retainAll(workerBClaims);

            Set<UUID> allClaims = new HashSet<>(workerAClaims);
            allClaims.addAll(workerBClaims);

            assertEquals(10, workerAClaims.size());
            assertEquals(10, workerBClaims.size());
            assertTrue(overlap.isEmpty());
            assertEquals(eventCount, allClaims.size());
            assertEquals(
                    eventCount,
                    outboxEventRepository.countByStatus(OutboxEventStatus.PROCESSING)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRecoverExpiredProcessingLeaseToPending() {
        OutboxEvent savedEvent = createPendingEvent();

        outboxEventClaimService.claimPendingEvents(10, "worker-a");

        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET processing_at = ?
                WHERE id = ?
                """,
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(2)),
                savedEvent.getId()
        );

        int recoveredCount = outboxEventClaimService
                .recoverExpiredProcessingEvents(
                        LocalDateTime.now().minusSeconds(60)
                );

        assertEquals(1, recoveredCount);

        OutboxEvent recoveredEvent = outboxEventRepository
                .findById(savedEvent.getId())
                .orElseThrow();

        assertEquals(OutboxEventStatus.PENDING, recoveredEvent.getStatus());
        assertNull(recoveredEvent.getProcessingAt());
        assertNull(recoveredEvent.getProcessingBy());
    }

    private OutboxEvent createPendingEvent() {
        return outboxEventRepository.saveAndFlush(
                new OutboxEvent(
                        "ORDER",
                        "claim-test-" + UUID.randomUUID(),
                        "ORDER_CREATED",
                        KafkaTopicConfig.ORDER_CREATED_TOPIC,
                        "{\"event\":\"claim-test\"}"
                )
        );
    }
}
