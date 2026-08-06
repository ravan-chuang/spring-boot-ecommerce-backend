package com.ravan.SpringBootLab.service;

import java.util.UUID;

public record DeadLetterReplayCommand(
        UUID id,
        String originalTopic,
        Integer originalPartition,
        String messageKey,
        String payload,
        UUID outboxEventId,
        String correlationId,
        Integer actorUserId,
        String actorEmail
) {
}
