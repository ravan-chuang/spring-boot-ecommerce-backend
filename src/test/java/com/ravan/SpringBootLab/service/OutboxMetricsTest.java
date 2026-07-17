package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {

    @Test
    void incrementsPublishCountersAndPositiveBatchCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxMetrics metrics = new OutboxMetrics(registry, repository);

        metrics.recordPublishSuccess();
        metrics.recordPublishFailure();
        metrics.recordClaimedEvents(3);
        metrics.recordRecoveredProcessingEvents(2);

        assertThat(registry.counter("outbox.publish.success").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("outbox.publish.failure").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("outbox.events.claimed").count())
                .isEqualTo(3.0);
        assertThat(registry.counter("outbox.processing.recovered").count())
                .isEqualTo(2.0);
    }

    @Test
    void ignoresZeroAndNegativeBatchCounts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxMetrics metrics = new OutboxMetrics(registry, repository);

        metrics.recordClaimedEvents(0);
        metrics.recordClaimedEvents(-1);
        metrics.recordRecoveredProcessingEvents(0);
        metrics.recordRecoveredProcessingEvents(-1);

        assertThat(registry.counter("outbox.events.claimed").count())
                .isZero();
        assertThat(registry.counter("outbox.processing.recovered").count())
                .isZero();
    }

    @Test
    void exposesStatusGaugesFromRepositoryCounts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxEventRepository repository = mock(OutboxEventRepository.class);

        when(repository.countByStatus(OutboxEventStatus.PENDING))
                .thenReturn(4L);
        when(repository.countByStatus(OutboxEventStatus.PROCESSING))
                .thenReturn(2L);
        when(repository.countByStatus(OutboxEventStatus.FAILED))
                .thenReturn(1L);

        new OutboxMetrics(registry, repository);

        assertThat(registry.get("outbox.events")
                .tag("status", "PENDING")
                .gauge()
                .value()).isEqualTo(4.0);
        assertThat(registry.get("outbox.events")
                .tag("status", "PROCESSING")
                .gauge()
                .value()).isEqualTo(2.0);
        assertThat(registry.get("outbox.events")
                .tag("status", "FAILED")
                .gauge()
                .value()).isEqualTo(1.0);
    }
}
