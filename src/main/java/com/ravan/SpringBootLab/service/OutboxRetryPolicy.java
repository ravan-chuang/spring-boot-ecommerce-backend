package com.ravan.SpringBootLab.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OutboxRetryPolicy {

    private final long baseDelaySeconds;
    private final long maxDelaySeconds;
    private final double jitterFactor;

    public OutboxRetryPolicy(
            @Value("${outbox.publisher.retry-base-delay-seconds:5}")
            long baseDelaySeconds,
            @Value("${outbox.publisher.retry-max-delay-seconds:300}")
            long maxDelaySeconds,
            @Value("${outbox.publisher.retry-jitter-factor:0.20}")
            double jitterFactor
    ) {
        if (baseDelaySeconds <= 0) {
            throw new IllegalArgumentException(
                    "Base retry delay must be positive"
            );
        }
        if (maxDelaySeconds < baseDelaySeconds) {
            throw new IllegalArgumentException(
                    "Maximum retry delay must not be smaller than base delay"
            );
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException(
                    "Jitter factor must be between 0.0 and 1.0"
            );
        }

        this.baseDelaySeconds = baseDelaySeconds;
        this.maxDelaySeconds = maxDelaySeconds;
        this.jitterFactor = jitterFactor;
    }

    public LocalDateTime calculateNextAttemptAt(
            int retryCount,
            LocalDateTime now
    ) {
        return now.plusSeconds(
                calculateDelaySeconds(
                        retryCount,
                        ThreadLocalRandom.current().nextDouble()
                )
        );
    }

    long calculateDelaySeconds(
            int retryCount,
            double jitterSample
    ) {
        if (retryCount < 1) {
            throw new IllegalArgumentException(
                    "Retry count must be at least one"
            );
        }
        if (jitterSample < 0.0 || jitterSample > 1.0) {
            throw new IllegalArgumentException(
                    "Jitter sample must be between 0.0 and 1.0"
            );
        }

        int exponent = Math.min(retryCount - 1, 62);

        long exponentialDelay;
        if (baseDelaySeconds > (Long.MAX_VALUE >> exponent)) {
            exponentialDelay = maxDelaySeconds;
        } else {
            exponentialDelay = baseDelaySeconds << exponent;
        }

        long cappedDelay = Math.min(
                exponentialDelay,
                maxDelaySeconds
        );

        long maximumJitter = Math.round(
                cappedDelay * jitterFactor
        );
        long jitter = Math.round(
                maximumJitter * jitterSample
        );

        return Math.min(
                maxDelaySeconds,
                cappedDelay + jitter
        );
    }
}
