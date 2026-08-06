package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.DeadLetterAuditAction;
import com.ravan.SpringBootLab.model.DeadLetterEvent;
import com.ravan.SpringBootLab.model.DeadLetterStatus;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.DeadLetterEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterStateServiceTest {

    private DeadLetterEventRepository repository;
    private DeadLetterAuditService auditService;
    private DeadLetterStateService service;
    private User actor;

    @BeforeEach
    void setUp() {
        repository = mock(DeadLetterEventRepository.class);
        auditService = mock(DeadLetterAuditService.class);
        service = new DeadLetterStateService(repository, auditService);
        actor = new User();
        actor.setId(7);
        actor.setEmail("operator@example.com");
    }

    @Test
    void listsAllOrFilteredEventsAndFindsDetails() {
        DeadLetterEvent event = event("list");
        PageRequest page = PageRequest.of(0, 20);
        when(repository.findAll(page)).thenReturn(new PageImpl<>(List.of(event)));
        when(repository.findByStatus(DeadLetterStatus.RECEIVED, page))
                .thenReturn(new PageImpl<>(List.of(event)));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThat(service.findEvents(null, page).getContent()).containsExactly(event);
        assertThat(service.findEvents(DeadLetterStatus.RECEIVED, page).getContent())
                .containsExactly(event);
        assertThat(service.getEvent(event.getId())).isSameAs(event);
    }

    @Test
    void completesAuditedStateMachine() {
        DeadLetterEvent event = event("state-machine");
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        assertThat(service.quarantine(event.getId(), actor, "reviewed").getStatus())
                .isEqualTo(DeadLetterStatus.QUARANTINED);
        DeadLetterReplayCommand command = service.reserveReplay(
                event.getId(), actor, "fixed"
        );
        assertThat(event.getStatus()).isEqualTo(DeadLetterStatus.REPLAYING);
        assertThat(command.actorUserId()).isEqualTo(7);
        assertThat(command.correlationId()).isEqualTo("correlation-state-machine");

        assertThat(service.completeReplay(command).getStatus())
                .isEqualTo(DeadLetterStatus.REPLAYED);
        assertThat(event.getReplayedBy()).isEqualTo(7);
        assertThat(event.getReplayStartedAt()).isNull();
        verify(auditService).record(
                event.getId(), DeadLetterAuditAction.QUARANTINED, actor, "reviewed"
        );
        verify(auditService).record(
                event.getId(), DeadLetterAuditAction.REPLAY_REQUESTED, actor, "fixed"
        );
        verify(auditService).record(
                eq(event.getId()), eq(DeadLetterAuditAction.REPLAY_SUCCEEDED),
                any(User.class), org.mockito.ArgumentMatchers.contains("order-created")
        );
    }

    @Test
    void failedReplayReturnsEventToQuarantine() {
        DeadLetterEvent event = event("failure");
        event.quarantine(actor.getId());
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        DeadLetterReplayCommand command = service.reserveReplay(event.getId(), actor, "try");

        service.failReplay(command, "broker unavailable");

        assertThat(event.getStatus()).isEqualTo(DeadLetterStatus.QUARANTINED);
        assertThat(event.getReplayStartedAt()).isNull();
        verify(auditService).record(
                eq(event.getId()), eq(DeadLetterAuditAction.REPLAY_FAILED),
                any(User.class), eq("broker unavailable")
        );

        service.failReplay(command, "ignored after lease released");
    }

    @Test
    void recoversOnlyExpiredReplayLeases() {
        DeadLetterEvent replaying = event("expired");
        replaying.quarantine(actor.getId());
        replaying.reserveReplay();
        DeadLetterEvent received = event("received");
        Instant cutoff = Instant.now().plusSeconds(1);
        when(repository.findByStatusAndReplayStartedAtBefore(
                DeadLetterStatus.REPLAYING, cutoff))
                .thenReturn(List.of(replaying, received));
        when(repository.findByIdForUpdate(replaying.getId()))
                .thenReturn(Optional.of(replaying));
        when(repository.findByIdForUpdate(received.getId()))
                .thenReturn(Optional.of(received));

        assertThat(service.recoverExpiredReplays(cutoff)).isEqualTo(1);
        assertThat(replaying.getStatus()).isEqualTo(DeadLetterStatus.QUARANTINED);
        verify(auditService).record(
                replaying.getId(), DeadLetterAuditAction.REPLAY_LEASE_RECOVERED,
                null, "Expired replay lease returned to quarantine"
        );
    }

    @Test
    void rejectsMissingOrInvalidStateTransitions() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());
        when(repository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEvent(missingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
        assertThatThrownBy(() -> service.quarantine(missingId, actor, "missing"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");

        DeadLetterEvent event = event("invalid");
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        assertThatThrownBy(() -> service.reserveReplay(event.getId(), actor, "early"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        event.quarantine(actor.getId());
        assertThatThrownBy(() -> service.quarantine(event.getId(), actor, "again"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    private DeadLetterEvent event(String key) {
        return new DeadLetterEvent(
                "order-created-dlt", 0, Math.abs(UUID.randomUUID().getLeastSignificantBits()),
                "order-created", 0, 1L, key, "payload", "{}",
                UUID.randomUUID(), "correlation-" + key,
                "java.lang.IllegalStateException", "test"
        );
    }
}
