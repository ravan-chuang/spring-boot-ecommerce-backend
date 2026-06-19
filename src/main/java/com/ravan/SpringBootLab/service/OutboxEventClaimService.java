package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxEventClaimService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventClaimService(
            OutboxEventRepository outboxEventRepository
    ) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public List<UUID> claimPendingEvents(
            int batchSize,
            String instanceId
    ) {
        List<OutboxEvent> events =
                outboxEventRepository.findNextPendingEventsForClaim(batchSize);

        events.forEach(event -> event.claimForProcessing(instanceId));
        outboxEventRepository.saveAll(events);

        return events.stream()
                .map(OutboxEvent::getId)
                .toList();
    }

    @Transactional
    public int recoverExpiredProcessingEvents(LocalDateTime expiredBefore) {
        return outboxEventRepository.recoverExpiredProcessingEvents(
                OutboxEventStatus.PENDING,
                OutboxEventStatus.PROCESSING,
                expiredBefore
        );
    }
}
