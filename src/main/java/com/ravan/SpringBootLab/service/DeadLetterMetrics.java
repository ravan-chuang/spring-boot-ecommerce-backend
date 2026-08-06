package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.DeadLetterStatus;
import com.ravan.SpringBootLab.repository.DeadLetterEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterMetrics {

    private final Counter capturedCounter;
    private final Counter quarantinedCounter;
    private final Counter replaySuccessCounter;
    private final Counter replayFailureCounter;
    private final Counter replayRecoveredCounter;

    public DeadLetterMetrics(
            MeterRegistry meterRegistry,
            DeadLetterEventRepository deadLetterEventRepository
    ) {
        for (DeadLetterStatus status : DeadLetterStatus.values()) {
            Gauge.builder(
                            "dlt.events",
                            deadLetterEventRepository,
                            repository -> repository.countByStatus(status)
                    )
                    .tag("status", status.name())
                    .description("Persisted dead-letter events by operator state")
                    .register(meterRegistry);
        }

        this.capturedCounter = Counter.builder("dlt.captured")
                .description("Terminal Kafka messages captured for operations")
                .register(meterRegistry);
        this.quarantinedCounter = Counter.builder("dlt.quarantined")
                .description("Dead-letter events explicitly quarantined")
                .register(meterRegistry);
        this.replaySuccessCounter = Counter.builder("dlt.replay.success")
                .description("Dead-letter replay sends acknowledged by Kafka")
                .register(meterRegistry);
        this.replayFailureCounter = Counter.builder("dlt.replay.failure")
                .description("Dead-letter replay sends that failed")
                .register(meterRegistry);
        this.replayRecoveredCounter = Counter.builder("dlt.replay.recovered")
                .description("Expired replay leases returned to quarantine")
                .register(meterRegistry);
    }

    public void recordCaptured() { capturedCounter.increment(); }
    public void recordQuarantined() { quarantinedCounter.increment(); }
    public void recordReplaySuccess() { replaySuccessCounter.increment(); }
    public void recordReplayFailure() { replayFailureCounter.increment(); }
    public void recordReplayRecovered(int count) { replayRecoveredCounter.increment(count); }
}
