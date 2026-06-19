package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.OutboxEventResponse;
import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class OutboxAdminService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxAdminService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(readOnly = true)
    public List<OutboxEventResponse> getFailedEvents() {
        return outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxEventStatus.FAILED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OutboxEventResponse replayFailedEvent(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Outbox event not found: " + eventId
                ));

        if (event.getStatus() != OutboxEventStatus.FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only FAILED outbox events can be replayed"
            );
        }

        event.replay();

        return toResponse(outboxEventRepository.save(event));
    }

    private OutboxEventResponse toResponse(OutboxEvent event) {
        return new OutboxEventResponse(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getTopic(),
                event.getPayload(),
                event.getStatus(),
                event.getRetryCount(),
                event.getCreatedAt(),
                event.getPublishedAt(),
                event.getLastError()
        );
    }
}
