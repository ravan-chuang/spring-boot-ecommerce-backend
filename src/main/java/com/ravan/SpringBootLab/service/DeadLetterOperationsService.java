package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.DeadLetterAuditResponse;
import com.ravan.SpringBootLab.dto.DeadLetterEventResponse;
import com.ravan.SpringBootLab.dto.PageResponse;
import com.ravan.SpringBootLab.model.DeadLetterAuditLog;
import com.ravan.SpringBootLab.model.DeadLetterEvent;
import com.ravan.SpringBootLab.model.DeadLetterStatus;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.DeadLetterAuditLogRepository;
import com.ravan.SpringBootLab.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class DeadLetterOperationsService {

    private final DeadLetterStateService deadLetterStateService;
    private final DeadLetterAuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;
    private final EventProducer eventProducer;
    private final DeadLetterMetrics deadLetterMetrics;

    public DeadLetterOperationsService(
            DeadLetterStateService deadLetterStateService,
            DeadLetterAuditLogRepository auditLogRepository,
            CurrentUserService currentUserService,
            EventProducer eventProducer,
            DeadLetterMetrics deadLetterMetrics
    ) {
        this.deadLetterStateService = deadLetterStateService;
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
        this.eventProducer = eventProducer;
        this.deadLetterMetrics = deadLetterMetrics;
    }

    public PageResponse<DeadLetterEventResponse> findEvents(
            DeadLetterStatus status,
            Pageable pageable
    ) {
        Page<DeadLetterEvent> events = deadLetterStateService.findEvents(
                status,
                pageable
        );

        return new PageResponse<>(
                events.getContent().stream().map(this::toResponse).toList(),
                events.getNumber(),
                events.getSize(),
                events.getTotalElements(),
                events.getTotalPages()
        );
    }

    public DeadLetterEventResponse getEvent(UUID eventId) {
        return toResponse(deadLetterStateService.getEvent(eventId));
    }

    public List<DeadLetterAuditResponse> getAuditHistory(UUID eventId) {
        deadLetterStateService.getEvent(eventId);

        return auditLogRepository
                .findByDeadLetterEventIdOrderByCreatedAtAsc(eventId)
                .stream()
                .map(this::toAuditResponse)
                .toList();
    }

    public DeadLetterEventResponse quarantine(
            UUID eventId,
            String reason
    ) {
        User actor = currentUserService.getCurrentUser();
        DeadLetterEvent event = deadLetterStateService.quarantine(
                eventId,
                actor,
                reason
        );

        deadLetterMetrics.recordQuarantined();
        return toResponse(event);
    }

    public DeadLetterEventResponse replay(
            UUID eventId,
            String reason
    ) {
        User actor = currentUserService.getCurrentUser();
        DeadLetterReplayCommand command = deadLetterStateService.reserveReplay(
                eventId,
                actor,
                reason
        );

        try {
            eventProducer.send(
                    command.originalTopic(),
                    command.originalPartition(),
                    command.messageKey(),
                    command.payload(),
                    command.outboxEventId(),
                    command.correlationId()
            );

            DeadLetterEvent replayed = deadLetterStateService.completeReplay(
                    command
            );
            deadLetterMetrics.recordReplaySuccess();
            return toResponse(replayed);
        } catch (RuntimeException exception) {
            deadLetterStateService.failReplay(
                    command,
                    "Kafka replay failed: " + safeMessage(exception)
            );
            deadLetterMetrics.recordReplayFailure();

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Kafka replay failed; event returned to quarantine",
                    exception
            );
        }
    }

    private DeadLetterEventResponse toResponse(DeadLetterEvent event) {
        return new DeadLetterEventResponse(
                event.getId(),
                event.getDltTopic(),
                event.getDltPartition(),
                event.getDltOffset(),
                event.getOriginalTopic(),
                event.getOriginalPartition(),
                event.getOriginalOffset(),
                event.getMessageKey(),
                event.getPayload(),
                event.getHeadersJson(),
                event.getOutboxEventId(),
                event.getCorrelationId(),
                event.getExceptionClass(),
                event.getExceptionMessage(),
                event.getStatus(),
                event.getReceivedAt(),
                event.getUpdatedAt(),
                event.getQuarantinedAt(),
                event.getQuarantinedBy(),
                event.getReplayStartedAt(),
                event.getReplayAttempts(),
                event.getReplayedAt(),
                event.getReplayedBy()
        );
    }

    private DeadLetterAuditResponse toAuditResponse(
            DeadLetterAuditLog auditLog
    ) {
        return new DeadLetterAuditResponse(
                auditLog.getId(),
                auditLog.getAction(),
                auditLog.getActorUserId(),
                auditLog.getActorEmail(),
                auditLog.getDetails(),
                auditLog.getCreatedAt()
        );
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
