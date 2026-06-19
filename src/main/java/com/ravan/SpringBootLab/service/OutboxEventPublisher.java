package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "outbox.publisher.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxEventPublisher {

    private static final Logger logger =
            LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventClaimService outboxEventClaimService;
    private final EventProducer eventProducer;
    private final int maxAttempts;
    private final int batchSize;
    private final long processingLeaseSeconds;
    private final String instanceId;
    private final OutboxMetrics outboxMetrics;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            OutboxEventClaimService outboxEventClaimService,
            EventProducer eventProducer,
            OutboxMetrics outboxMetrics,
            @Value("${outbox.publisher.max-attempts:10}") int maxAttempts,
            @Value("${outbox.publisher.batch-size:50}") int batchSize,
            @Value("${outbox.publisher.processing-lease-seconds:60}") long processingLeaseSeconds
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventClaimService = outboxEventClaimService;
        this.eventProducer = eventProducer;
        this.outboxMetrics = outboxMetrics;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.processingLeaseSeconds = processingLeaseSeconds;
        this.instanceId = UUID.randomUUID().toString();
    }

    @Scheduled(
            fixedDelayString = "${outbox.publisher.fixed-delay-ms:1000}",
            initialDelayString = "${outbox.publisher.initial-delay-ms:3000}"
    )
    public void publishPendingEvents() {
        recoverExpiredProcessingEvents();

        List<UUID> claimedEventIds =
                outboxEventClaimService.claimPendingEvents(
                        batchSize,
                        instanceId
                );

        outboxMetrics.recordClaimedEvents(claimedEventIds.size());

        for (UUID eventId : claimedEventIds) {
            publishClaimedEvent(eventId);
        }
    }

    private void recoverExpiredProcessingEvents() {
        LocalDateTime expiredBefore = LocalDateTime.now()
                .minusSeconds(processingLeaseSeconds);

        int recoveredCount = outboxEventClaimService
                .recoverExpiredProcessingEvents(expiredBefore);

        outboxMetrics.recordRecoveredProcessingEvents(recoveredCount);

        if (recoveredCount > 0) {
            logger.warn(
                    "Recovered expired outbox processing leases: count={}",
                    recoveredCount
            );
        }
    }

    private void publishClaimedEvent(UUID eventId) {
        OutboxEvent event = outboxEventRepository
                .findByIdAndStatusAndProcessingBy(
                        eventId,
                        OutboxEventStatus.PROCESSING,
                        instanceId
                )
                .orElse(null);

        if (event == null) {
            return;
        }

        try {
            eventProducer.send(
                    event.getTopic(),
                    event.getAggregateId(),
                    event.getPayload()
            );

            event.markPublished();
            outboxEventRepository.save(event);

            outboxMetrics.recordPublishSuccess();

            logger.info(
                    "Published claimed outbox event: id={}, eventType={}, topic={}, instanceId={}",
                    event.getId(),
                    event.getEventType(),
                    event.getTopic(),
                    instanceId
            );
        } catch (RuntimeException exception) {
            String errorMessage = exception.getMessage();
            outboxMetrics.recordPublishFailure();

            if (event.getRetryCount() + 1 >= maxAttempts) {
                event.markFailed(errorMessage);

                logger.error(
                        "Outbox event marked FAILED after max attempts: id={}, retryCount={}, error={}",
                        event.getId(),
                        event.getRetryCount(),
                        errorMessage
                );
            } else {
                event.releaseForRetry(errorMessage);

                logger.warn(
                        "Outbox publish failed; released for retry: id={}, retryCount={}, error={}",
                        event.getId(),
                        event.getRetryCount(),
                        errorMessage
                );
            }

            outboxEventRepository.save(event);
        }
    }
}
