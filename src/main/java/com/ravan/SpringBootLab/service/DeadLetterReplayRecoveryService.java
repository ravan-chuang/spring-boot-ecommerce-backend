package com.ravan.SpringBootLab.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DeadLetterReplayRecoveryService {

    private static final Logger logger =
            LoggerFactory.getLogger(DeadLetterReplayRecoveryService.class);

    private final DeadLetterStateService deadLetterStateService;
    private final DeadLetterMetrics deadLetterMetrics;
    private final long replayLeaseSeconds;

    public DeadLetterReplayRecoveryService(
            DeadLetterStateService deadLetterStateService,
            DeadLetterMetrics deadLetterMetrics,
            @Value("${dlt.replay.lease-seconds:60}") long replayLeaseSeconds
    ) {
        this.deadLetterStateService = deadLetterStateService;
        this.deadLetterMetrics = deadLetterMetrics;
        this.replayLeaseSeconds = replayLeaseSeconds;
    }

    @Scheduled(
            fixedDelayString = "${dlt.replay.recovery.fixed-delay-ms:30000}",
            initialDelayString = "${dlt.replay.recovery.initial-delay-ms:30000}"
    )
    public void recoverExpiredReplays() {
        int recovered = deadLetterStateService.recoverExpiredReplays(
                Instant.now().minusSeconds(replayLeaseSeconds)
        );

        deadLetterMetrics.recordReplayRecovered(recovered);

        if (recovered > 0) {
            logger.warn(
                    "Recovered expired dead-letter replay leases: count={}",
                    recovered
            );
        }
    }
}
