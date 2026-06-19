package com.ravan.SpringBootLab.dto;

import com.ravan.SpringBootLab.model.OutboxEventStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxEventResponse(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String payload,
        OutboxEventStatus status,
        Integer retryCount,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        String lastError
) {
}
