package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class OutboxMetrics {

    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;
    private final Counter claimedCounter;
    private final Counter recoveredProcessingCounter;

    public OutboxMetrics(
            MeterRegistry meterRegistry,
            OutboxEventRepository outboxEventRepository
    ) {
        this.publishSuccessCounter = Counter.builder("outbox.publish.success")
                .description("Number of outbox events successfully published to Kafka")
                .register(meterRegistry);

        this.publishFailureCounter = Counter.builder("outbox.publish.failure")
                .description("Number of failed outbox publish attempts")
                .register(meterRegistry);

        this.claimedCounter = Counter.builder("outbox.events.claimed")
                .description("Number of outbox events claimed for processing")
                .register(meterRegistry);

        this.recoveredProcessingCounter = Counter.builder("outbox.processing.recovered")
                .description("Number of expired outbox processing leases recovered")
                .register(meterRegistry);

        registerStatusGauge(
                meterRegistry,
                outboxEventRepository,
                OutboxEventStatus.PENDING
        );
        registerStatusGauge(
                meterRegistry,
                outboxEventRepository,
                OutboxEventStatus.PROCESSING
        );
        registerStatusGauge(
                meterRegistry,
                outboxEventRepository,
                OutboxEventStatus.FAILED
        );
    }

    public void recordPublishSuccess() {
        publishSuccessCounter.increment();
    }

    public void recordPublishFailure() {
        publishFailureCounter.increment();
    }

    public void recordClaimedEvents(int count) {
        if (count > 0) {
            claimedCounter.increment(count);
        }
    }

    public void recordRecoveredProcessingEvents(int count) {
        if (count > 0) {
            recoveredProcessingCounter.increment(count);
        }
    }

    private void registerStatusGauge(
            MeterRegistry meterRegistry,
            OutboxEventRepository outboxEventRepository,
            OutboxEventStatus status
    ) {
        Gauge.builder(
                        "outbox.events",
                        outboxEventRepository,
                        repository -> repository.countByStatus(status)
                )
                .description("Current number of outbox events by status")
                .tag("status", status.name())
                .register(meterRegistry);
    }
}
