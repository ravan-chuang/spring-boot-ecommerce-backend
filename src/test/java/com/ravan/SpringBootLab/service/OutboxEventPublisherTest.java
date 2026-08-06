package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class OutboxEventPublisherTest {

    private OutboxEventRepository repository;
    private OutboxEventClaimService claimService;
    private EventProducer eventProducer;
    private OutboxMetrics metrics;
    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        claimService = mock(OutboxEventClaimService.class);
        eventProducer = mock(EventProducer.class);
        metrics = mock(OutboxMetrics.class);

        publisher = new OutboxEventPublisher(
                repository,
                claimService,
                eventProducer,
                metrics,
                3,
                10,
                60
        );
    }

    @Test
    void recordsZeroClaimsWhenNoPendingEventsExist() {
        when(claimService.recoverExpiredProcessingEvents(any()))
                .thenReturn(0);
        when(claimService.claimPendingEvents(eq(10), anyString()))
                .thenReturn(List.of());

        publisher.publishPendingEvents();

        verify(metrics).recordRecoveredProcessingEvents(0);
        verify(metrics).recordClaimedEvents(0);
        verify(repository, never()).findByIdAndStatusAndProcessingBy(
                any(),
                any(),
                anyString()
        );
        verify(eventProducer, never()).send(
                anyString(),
                any(),
                anyString(),
                anyString(),
                any(),
                any()
        );
    }

    @Test
    void skipsClaimThatNoLongerBelongsToThisPublisherInstance() {
        UUID eventId = UUID.randomUUID();

        when(claimService.recoverExpiredProcessingEvents(any()))
                .thenReturn(2);
        when(claimService.claimPendingEvents(eq(10), anyString()))
                .thenReturn(List.of(eventId));
        when(repository.findByIdAndStatusAndProcessingBy(
                eq(eventId),
                eq(OutboxEventStatus.PROCESSING),
                anyString()
        )).thenReturn(Optional.empty());

        publisher.publishPendingEvents();

        verify(metrics).recordRecoveredProcessingEvents(2);
        verify(metrics).recordClaimedEvents(1);
        verify(eventProducer, never()).send(
                anyString(),
                any(),
                anyString(),
                anyString(),
                any(),
                any()
        );
        verify(repository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void publishesClaimedEventAndMarksItPublished() {
        OutboxEvent event = createEvent();
        UUID eventId = event.getId();

        when(claimService.recoverExpiredProcessingEvents(any()))
                .thenReturn(0);
        when(claimService.claimPendingEvents(eq(10), anyString()))
                .thenReturn(List.of(eventId));
        when(repository.findByIdAndStatusAndProcessingBy(
                eq(eventId),
                eq(OutboxEventStatus.PROCESSING),
                anyString()
        )).thenReturn(Optional.of(event));

        publisher.publishPendingEvents();

        verify(eventProducer).send(
                event.getTopic(),
                null,
                event.getAggregateId(),
                event.getPayload(),
                eventId,
                event.getCorrelationId()
        );
        verify(repository).save(event);
        verify(metrics).recordPublishSuccess();
        verify(metrics, never()).recordPublishFailure();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void releasesEventForRetryBeforeMaximumAttempts() {
        OutboxEvent event = createEvent();

        prepareClaimedEvent(event);
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(eventProducer)
                .send(
                        event.getTopic(),
                        null,
                        event.getAggregateId(),
                        event.getPayload(),
                        event.getId(),
                        event.getCorrelationId()
                );

        publisher.publishPendingEvents();

        verify(metrics).recordPublishFailure();
        verify(metrics, never()).recordPublishSuccess();
        verify(repository).save(event);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("Kafka unavailable");
        assertThat(event.getProcessingAt()).isNull();
        assertThat(event.getProcessingBy()).isNull();
    }

    @Test
    void marksEventFailedAtMaximumAttempts() {
        OutboxEvent event = createEvent();
        event.releaseForRetry("first failure");
        event.releaseForRetry("second failure");

        prepareClaimedEvent(event);
        doThrow(new RuntimeException("Kafka still unavailable"))
                .when(eventProducer)
                .send(
                        event.getTopic(),
                        null,
                        event.getAggregateId(),
                        event.getPayload(),
                        event.getId(),
                        event.getCorrelationId()
                );

        publisher.publishPendingEvents();

        verify(metrics).recordPublishFailure();
        verify(repository).save(event);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getLastError()).isEqualTo("Kafka still unavailable");
        assertThat(event.getProcessingAt()).isNull();
        assertThat(event.getProcessingBy()).isNull();
    }

    private void prepareClaimedEvent(OutboxEvent event) {
        when(claimService.recoverExpiredProcessingEvents(any()))
                .thenReturn(0);
        when(claimService.claimPendingEvents(eq(10), anyString()))
                .thenReturn(List.of(event.getId()));
        when(repository.findByIdAndStatusAndProcessingBy(
                eq(event.getId()),
                eq(OutboxEventStatus.PROCESSING),
                anyString()
        )).thenReturn(Optional.of(event));
    }

    private OutboxEvent createEvent() {
        return new OutboxEvent(
                "ORDER",
                "order-123",
                "ORDER_CREATED",
                "order-created",
                "{\"orderId\":\"order-123\"}",
                "publisher-test-correlation"
        );
    }
}
