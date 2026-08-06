package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.DeadLetterAuditAction;
import com.ravan.SpringBootLab.model.DeadLetterEvent;
import com.ravan.SpringBootLab.model.DeadLetterStatus;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.DeadLetterEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeadLetterStateService {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final DeadLetterAuditService deadLetterAuditService;

    public DeadLetterStateService(
            DeadLetterEventRepository deadLetterEventRepository,
            DeadLetterAuditService deadLetterAuditService
    ) {
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.deadLetterAuditService = deadLetterAuditService;
    }

    @Transactional(readOnly = true)
    public Page<DeadLetterEvent> findEvents(
            DeadLetterStatus status,
            Pageable pageable
    ) {
        return status == null
                ? deadLetterEventRepository.findAll(pageable)
                : deadLetterEventRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public DeadLetterEvent getEvent(UUID eventId) {
        return deadLetterEventRepository.findById(eventId)
                .orElseThrow(() -> notFound(eventId));
    }

    @Transactional
    public DeadLetterEvent quarantine(
            UUID eventId,
            User actor,
            String reason
    ) {
        DeadLetterEvent event = findLocked(eventId);

        if (event.getStatus() != DeadLetterStatus.RECEIVED) {
            throw conflict(
                    "Only RECEIVED dead-letter events can be quarantined"
            );
        }

        event.quarantine(actor.getId());
        deadLetterAuditService.record(
                eventId,
                DeadLetterAuditAction.QUARANTINED,
                actor,
                reason
        );

        return event;
    }

    @Transactional
    public DeadLetterReplayCommand reserveReplay(
            UUID eventId,
            User actor,
            String reason
    ) {
        DeadLetterEvent event = findLocked(eventId);

        if (event.getStatus() != DeadLetterStatus.QUARANTINED) {
            throw conflict(
                    "Only QUARANTINED dead-letter events can be replayed"
            );
        }

        event.reserveReplay();
        deadLetterAuditService.record(
                eventId,
                DeadLetterAuditAction.REPLAY_REQUESTED,
                actor,
                reason
        );

        return new DeadLetterReplayCommand(
                event.getId(),
                event.getOriginalTopic(),
                event.getOriginalPartition(),
                event.getMessageKey(),
                event.getPayload(),
                event.getOutboxEventId(),
                event.getCorrelationId(),
                actor.getId(),
                actor.getEmail()
        );
    }

    @Transactional
    public DeadLetterEvent completeReplay(
            DeadLetterReplayCommand command
    ) {
        DeadLetterEvent event = findLocked(command.id());

        requireReplaying(event);
        event.markReplaySucceeded(command.actorUserId());
        deadLetterAuditService.record(
                event.getId(),
                DeadLetterAuditAction.REPLAY_SUCCEEDED,
                actorSnapshot(command),
                "Kafka acknowledged replay to " + command.originalTopic()
        );

        return event;
    }

    @Transactional
    public void failReplay(
            DeadLetterReplayCommand command,
            String errorMessage
    ) {
        DeadLetterEvent event = findLocked(command.id());

        if (event.getStatus() != DeadLetterStatus.REPLAYING) {
            return;
        }

        event.markReplayFailed();
        deadLetterAuditService.record(
                event.getId(),
                DeadLetterAuditAction.REPLAY_FAILED,
                actorSnapshot(command),
                errorMessage
        );
    }

    @Transactional
    public int recoverExpiredReplays(Instant expiredBefore) {
        List<DeadLetterEvent> candidates = deadLetterEventRepository
                .findByStatusAndReplayStartedAtBefore(
                        DeadLetterStatus.REPLAYING,
                        expiredBefore
                );
        int recovered = 0;

        for (DeadLetterEvent candidate : candidates) {
            DeadLetterEvent event = findLocked(candidate.getId());

            if (event.getStatus() == DeadLetterStatus.REPLAYING
                    && event.getReplayStartedAt() != null
                    && event.getReplayStartedAt().isBefore(expiredBefore)) {
                event.recoverExpiredReplay();
                deadLetterAuditService.record(
                        event.getId(),
                        DeadLetterAuditAction.REPLAY_LEASE_RECOVERED,
                        null,
                        "Expired replay lease returned to quarantine"
                );
                recovered++;
            }
        }

        return recovered;
    }

    private DeadLetterEvent findLocked(UUID eventId) {
        return deadLetterEventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> notFound(eventId));
    }

    private void requireReplaying(DeadLetterEvent event) {
        if (event.getStatus() != DeadLetterStatus.REPLAYING) {
            throw conflict("Dead-letter event is no longer reserved for replay");
        }
    }

    private User actorSnapshot(DeadLetterReplayCommand command) {
        User actor = new User();
        actor.setId(command.actorUserId());
        actor.setEmail(command.actorEmail());
        return actor;
    }

    private ResponseStatusException notFound(UUID eventId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Dead-letter event not found: " + eventId
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
