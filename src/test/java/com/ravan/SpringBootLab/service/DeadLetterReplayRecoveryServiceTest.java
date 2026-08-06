package com.ravan.SpringBootLab.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterReplayRecoveryServiceTest {

    @Test
    void recoversEventsOlderThanConfiguredLeaseAndRecordsMetric() {
        DeadLetterStateService stateService = mock(DeadLetterStateService.class);
        DeadLetterMetrics metrics = mock(DeadLetterMetrics.class);
        when(stateService.recoverExpiredReplays(org.mockito.ArgumentMatchers.any()))
                .thenReturn(2);
        DeadLetterReplayRecoveryService service =
                new DeadLetterReplayRecoveryService(stateService, metrics, 60);
        Instant before = Instant.now();

        service.recoverExpiredReplays();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(stateService).recoverExpiredReplays(cutoff.capture());
        assertThat(Duration.between(cutoff.getValue(), before).toSeconds())
                .isBetween(59L, 61L);
        verify(metrics).recordReplayRecovered(2);
    }
}
