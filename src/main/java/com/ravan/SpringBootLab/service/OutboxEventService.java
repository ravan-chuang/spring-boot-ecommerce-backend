package com.ravan.SpringBootLab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void saveEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            Object payload
    ) {
        try {
            String serializedPayload = objectMapper.writeValueAsString(payload);

            OutboxEvent outboxEvent = new OutboxEvent(
                    aggregateType,
                    aggregateId,
                    eventType,
                    topic,
                    serializedPayload
            );

            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize outbox event payload",
                    exception
            );
        }
    }
}
