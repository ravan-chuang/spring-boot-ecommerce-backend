package com.ravan.SpringBootLab.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventRetryScheduleTest {

    @Test
    void initializesAsImmediatelyEligible() {
        LocalDateTime before = LocalDateTime.now();

        OutboxEvent event = createEvent();

        assertThat(event.getNextAttemptAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void schedulesRetryAtProvidedTime() {
        OutboxEvent event = createEvent();
        LocalDateTime scheduledAt =
                LocalDateTime.of(2026, 8, 7, 3, 15);

        event.claimForProcessing("publisher-1");
        event.releaseForRetry("Kafka unavailable", scheduledAt);

        assertThat(event.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(scheduledAt);
        assertThat(event.getProcessingAt()).isNull();
        assertThat(event.getProcessingBy()).isNull();
    }

    @Test
    void clearsScheduleWhenPublishedOrPermanentlyFailed() {
        OutboxEvent published = createEvent();
        published.markPublished();

        assertThat(published.getNextAttemptAt()).isNull();

        OutboxEvent failed = createEvent();
        failed.markFailed("Permanent failure");

        assertThat(failed.getNextAttemptAt()).isNull();
    }

    @Test
    void resetsScheduleForImmediateReplay() {
        OutboxEvent event = createEvent();
        event.markFailed("Failure");

        LocalDateTime before = LocalDateTime.now();
        event.replay();

        assertThat(event.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getNextAttemptAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(LocalDateTime.now());
    }

    private OutboxEvent createEvent() {
        return new OutboxEvent(
                "ORDER",
                "order-123",
                "ORDER_CREATED",
                "order-created",
                "{\"orderId\":\"order-123\"}",
                "retry-test-correlation"
        );
    }
}
