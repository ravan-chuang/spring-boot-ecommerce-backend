package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.DeadLetterStatus;
import com.ravan.SpringBootLab.repository.DeadLetterEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeadLetterMetricsTest {

    @Test
    void exposesStateGaugesAndOperationCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeadLetterEventRepository repository = mock(DeadLetterEventRepository.class);
        when(repository.countByStatus(DeadLetterStatus.RECEIVED)).thenReturn(3L);
        when(repository.countByStatus(DeadLetterStatus.QUARANTINED)).thenReturn(2L);
        when(repository.countByStatus(DeadLetterStatus.REPLAYING)).thenReturn(1L);
        when(repository.countByStatus(DeadLetterStatus.REPLAYED)).thenReturn(4L);

        DeadLetterMetrics metrics = new DeadLetterMetrics(registry, repository);
        metrics.recordCaptured();
        metrics.recordQuarantined();
        metrics.recordReplaySuccess();
        metrics.recordReplayFailure();
        metrics.recordReplayRecovered(2);

        assertThat(registry.get("dlt.events").tag("status", "RECEIVED")
                .gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("dlt.events").tag("status", "REPLAYED")
                .gauge().value()).isEqualTo(4.0);
        assertThat(registry.counter("dlt.captured").count()).isEqualTo(1.0);
        assertThat(registry.counter("dlt.quarantined").count()).isEqualTo(1.0);
        assertThat(registry.counter("dlt.replay.success").count()).isEqualTo(1.0);
        assertThat(registry.counter("dlt.replay.failure").count()).isEqualTo(1.0);
        assertThat(registry.counter("dlt.replay.recovered").count()).isEqualTo(2.0);
    }
}
