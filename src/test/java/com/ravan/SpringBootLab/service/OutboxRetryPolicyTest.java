package com.ravan.SpringBootLab.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxRetryPolicyTest {

    private final OutboxRetryPolicy policy =
            new OutboxRetryPolicy(10, 100, 0.20);

    @Test
    void appliesExponentialBackoffWithoutJitter() {
        assertThat(policy.calculateDelaySeconds(1, 0.0))
                .isEqualTo(10);
        assertThat(policy.calculateDelaySeconds(2, 0.0))
                .isEqualTo(20);
        assertThat(policy.calculateDelaySeconds(3, 0.0))
                .isEqualTo(40);
    }

    @Test
    void appliesBoundedPositiveJitter() {
        assertThat(policy.calculateDelaySeconds(1, 0.0))
                .isEqualTo(10);
        assertThat(policy.calculateDelaySeconds(1, 1.0))
                .isEqualTo(12);
        assertThat(policy.calculateDelaySeconds(3, 0.5))
                .isEqualTo(44);
    }

    @Test
    void capsDelayAtConfiguredMaximum() {
        assertThat(policy.calculateDelaySeconds(4, 0.0))
                .isEqualTo(80);
        assertThat(policy.calculateDelaySeconds(4, 1.0))
                .isEqualTo(96);
        assertThat(policy.calculateDelaySeconds(5, 1.0))
                .isEqualTo(100);
        assertThat(policy.calculateDelaySeconds(30, 1.0))
                .isEqualTo(100);
    }

    @Test
    void calculatesNextAttemptFromProvidedTime() {
        LocalDateTime now =
                LocalDateTime.of(2026, 8, 7, 2, 30);

        assertThat(policy.calculateNextAttemptAt(2, now))
                .isAfterOrEqualTo(now.plusSeconds(20))
                .isBeforeOrEqualTo(now.plusSeconds(24));
    }

    @Test
    void rejectsInvalidConfigurationAndInputs() {
        assertThatThrownBy(
                () -> new OutboxRetryPolicy(0, 100, 0.2)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> new OutboxRetryPolicy(10, 5, 0.2)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> new OutboxRetryPolicy(10, 100, 1.1)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> policy.calculateDelaySeconds(0, 0.5)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> policy.calculateDelaySeconds(1, -0.1)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
