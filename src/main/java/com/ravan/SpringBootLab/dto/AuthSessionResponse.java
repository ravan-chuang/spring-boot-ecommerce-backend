package com.ravan.SpringBootLab.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionResponse(
        UUID sessionId,
        String deviceName,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt
) {
}
