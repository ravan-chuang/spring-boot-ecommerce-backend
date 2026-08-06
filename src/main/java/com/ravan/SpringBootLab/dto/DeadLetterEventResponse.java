package com.ravan.SpringBootLab.dto;

import com.ravan.SpringBootLab.model.DeadLetterStatus;

import java.time.Instant;
import java.util.UUID;

public class DeadLetterEventResponse {

    private final UUID id;
    private final String dltTopic;
    private final Integer dltPartition;
    private final Long dltOffset;
    private final String originalTopic;
    private final Integer originalPartition;
    private final Long originalOffset;
    private final String messageKey;
    private final String payload;
    private final String headersJson;
    private final UUID outboxEventId;
    private final String correlationId;
    private final String exceptionClass;
    private final String exceptionMessage;
    private final DeadLetterStatus status;
    private final Instant receivedAt;
    private final Instant updatedAt;
    private final Instant quarantinedAt;
    private final Integer quarantinedBy;
    private final Instant replayStartedAt;
    private final Integer replayAttempts;
    private final Instant replayedAt;
    private final Integer replayedBy;

    public DeadLetterEventResponse(
            UUID id,
            String dltTopic,
            Integer dltPartition,
            Long dltOffset,
            String originalTopic,
            Integer originalPartition,
            Long originalOffset,
            String messageKey,
            String payload,
            String headersJson,
            UUID outboxEventId,
            String correlationId,
            String exceptionClass,
            String exceptionMessage,
            DeadLetterStatus status,
            Instant receivedAt,
            Instant updatedAt,
            Instant quarantinedAt,
            Integer quarantinedBy,
            Instant replayStartedAt,
            Integer replayAttempts,
            Instant replayedAt,
            Integer replayedBy
    ) {
        this.id = id;
        this.dltTopic = dltTopic;
        this.dltPartition = dltPartition;
        this.dltOffset = dltOffset;
        this.originalTopic = originalTopic;
        this.originalPartition = originalPartition;
        this.originalOffset = originalOffset;
        this.messageKey = messageKey;
        this.payload = payload;
        this.headersJson = headersJson;
        this.outboxEventId = outboxEventId;
        this.correlationId = correlationId;
        this.exceptionClass = exceptionClass;
        this.exceptionMessage = exceptionMessage;
        this.status = status;
        this.receivedAt = receivedAt;
        this.updatedAt = updatedAt;
        this.quarantinedAt = quarantinedAt;
        this.quarantinedBy = quarantinedBy;
        this.replayStartedAt = replayStartedAt;
        this.replayAttempts = replayAttempts;
        this.replayedAt = replayedAt;
        this.replayedBy = replayedBy;
    }

    public UUID getId() { return id; }
    public String getDltTopic() { return dltTopic; }
    public Integer getDltPartition() { return dltPartition; }
    public Long getDltOffset() { return dltOffset; }
    public String getOriginalTopic() { return originalTopic; }
    public Integer getOriginalPartition() { return originalPartition; }
    public Long getOriginalOffset() { return originalOffset; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public String getHeadersJson() { return headersJson; }
    public UUID getOutboxEventId() { return outboxEventId; }
    public String getCorrelationId() { return correlationId; }
    public String getExceptionClass() { return exceptionClass; }
    public String getExceptionMessage() { return exceptionMessage; }
    public DeadLetterStatus getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getQuarantinedAt() { return quarantinedAt; }
    public Integer getQuarantinedBy() { return quarantinedBy; }
    public Instant getReplayStartedAt() { return replayStartedAt; }
    public Integer getReplayAttempts() { return replayAttempts; }
    public Instant getReplayedAt() { return replayedAt; }
    public Integer getReplayedBy() { return replayedBy; }
}
