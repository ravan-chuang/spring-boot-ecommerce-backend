package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.DeadLetterAuditAction;
import com.ravan.SpringBootLab.model.DeadLetterAuditLog;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.DeadLetterAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeadLetterAuditServiceTest {

    @Test
    void recordsActorAndBoundsOperatorDetails() {
        DeadLetterAuditLogRepository repository = mock(DeadLetterAuditLogRepository.class);
        DeadLetterAuditService service = new DeadLetterAuditService(repository);
        User actor = new User();
        actor.setId(42);
        actor.setEmail("admin@example.com");
        UUID eventId = UUID.randomUUID();

        service.record(eventId, DeadLetterAuditAction.QUARANTINED, actor,
                "  " + "x".repeat(2_100) + "  ");

        ArgumentCaptor<DeadLetterAuditLog> captor =
                ArgumentCaptor.forClass(DeadLetterAuditLog.class);
        verify(repository).save(captor.capture());
        DeadLetterAuditLog log = captor.getValue();
        assertThat(log.getDeadLetterEventId()).isEqualTo(eventId);
        assertThat(log.getAction()).isEqualTo(DeadLetterAuditAction.QUARANTINED);
        assertThat(log.getActorUserId()).isEqualTo(42);
        assertThat(log.getActorEmail()).isEqualTo("admin@example.com");
        assertThat(log.getDetails()).hasSize(2_000);
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    void recordsSystemActionWithoutBlankDetails() {
        DeadLetterAuditLogRepository repository = mock(DeadLetterAuditLogRepository.class);
        DeadLetterAuditService service = new DeadLetterAuditService(repository);

        service.record(UUID.randomUUID(), DeadLetterAuditAction.CAPTURED, null, "   ");

        ArgumentCaptor<DeadLetterAuditLog> captor =
                ArgumentCaptor.forClass(DeadLetterAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActorUserId()).isNull();
        assertThat(captor.getValue().getActorEmail()).isNull();
        assertThat(captor.getValue().getDetails()).isNull();
    }
}
