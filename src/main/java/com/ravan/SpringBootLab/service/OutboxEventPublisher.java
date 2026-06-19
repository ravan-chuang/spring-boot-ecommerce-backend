package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

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
    private final EventProducer eventProducer;
    private final int maxAttempts;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            EventProducer eventProducer,
            @Value("${outbox.publisher.max-attempts:10}") int maxAttempts
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventProducer = eventProducer;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(
            fixedDelayString = "${outbox.publisher.fixed-delay-ms:1000}",
            initialDelayString = "${outbox.publisher.initial-delay-ms:3000}"
    )
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(
                        OutboxEventStatus.PENDING
                );

        for (OutboxEvent event : pendingEvents) {
            try {
                eventProducer.send(
                        event.getTopic(),
                        event.getAggregateId(),
                        event.getPayload()
                );

                event.markPublished();
                outboxEventRepository.save(event);

                logger.info(
                        "Published outbox event: id={}, eventType={}, topic={}",
                        event.getId(),
                        event.getEventType(),
                        event.getTopic()
                );
            } catch (RuntimeException exception) {
                String errorMessage = exception.getMessage();

                if (event.getRetryCount() + 1 >= maxAttempts) {
                    event.markFailed(errorMessage);
                    outboxEventRepository.save(event);

                    logger.error(
                            "Outbox event marked FAILED after max attempts: id={}, retryCount={}, error={}",
                            event.getId(),
                            event.getRetryCount(),
                            errorMessage
                    );
                } else {
                    event.markRetryableFailure(errorMessage);
                    outboxEventRepository.save(event);

                    logger.warn(
                            "Outbox publish failed; will retry: id={}, retryCount={}, error={}",
                            event.getId(),
                            event.getRetryCount(),
                            errorMessage
                    );
                }
            }
        }
    }
}
