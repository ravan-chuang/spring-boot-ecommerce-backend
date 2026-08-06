package com.ravan.SpringBootLab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEvent {

    @Id
    private UUID id;

    @Column(name = "dlt_topic", nullable = false)
    private String dltTopic;

    @Column(name = "dlt_partition", nullable = false)
    private Integer dltPartition;

    @Column(name = "dlt_offset", nullable = false)
    private Long dltOffset;

    @Column(name = "original_topic", nullable = false)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "message_key", length = 512)
    private String messageKey;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "headers_json", nullable = false, columnDefinition = "TEXT")
    private String headersJson;

    @Column(name = "outbox_event_id")
    private UUID outboxEventId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "exception_class")
    private String exceptionClass;

    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String exceptionMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeadLetterStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "quarantined_at")
    private Instant quarantinedAt;

    @Column(name = "quarantined_by")
    private Integer quarantinedBy;

    @Column(name = "replay_started_at")
    private Instant replayStartedAt;

    @Column(name = "replay_attempts", nullable = false)
    private Integer replayAttempts;

    @Column(name = "replayed_at")
    private Instant replayedAt;

    @Column(name = "replayed_by")
    private Integer replayedBy;

    public DeadLetterEvent() {
    }

    public DeadLetterEvent(
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
            String exceptionMessage
    ) {
        this.id = UUID.randomUUID();
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
        this.status = DeadLetterStatus.RECEIVED;
        this.receivedAt = Instant.now();
        this.updatedAt = this.receivedAt;
        this.replayAttempts = 0;
    }

    public void quarantine(Integer actorUserId) {
        Instant now = Instant.now();
        this.status = DeadLetterStatus.QUARANTINED;
        this.quarantinedAt = now;
        this.quarantinedBy = actorUserId;
        this.updatedAt = now;
    }

    public void reserveReplay() {
        this.status = DeadLetterStatus.REPLAYING;
        this.replayStartedAt = Instant.now();
        this.replayAttempts++;
        this.updatedAt = this.replayStartedAt;
    }

    public void markReplaySucceeded(Integer actorUserId) {
        Instant now = Instant.now();
        this.status = DeadLetterStatus.REPLAYED;
        this.replayedAt = now;
        this.replayedBy = actorUserId;
        this.replayStartedAt = null;
        this.updatedAt = now;
    }

    public void markReplayFailed() {
        this.status = DeadLetterStatus.QUARANTINED;
        this.replayStartedAt = null;
        this.updatedAt = Instant.now();
    }

    public void recoverExpiredReplay() {
        markReplayFailed();
    }

    public UUID getId() {
        return id;
    }

    public String getDltTopic() {
        return dltTopic;
    }

    public Integer getDltPartition() {
        return dltPartition;
    }

    public Long getDltOffset() {
        return dltOffset;
    }

    public String getOriginalTopic() {
        return originalTopic;
    }

    public Integer getOriginalPartition() {
        return originalPartition;
    }

    public Long getOriginalOffset() {
        return originalOffset;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public UUID getOutboxEventId() {
        return outboxEventId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public DeadLetterStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getQuarantinedAt() {
        return quarantinedAt;
    }

    public Integer getQuarantinedBy() {
        return quarantinedBy;
    }

    public Instant getReplayStartedAt() {
        return replayStartedAt;
    }

    public Integer getReplayAttempts() {
        return replayAttempts;
    }

    public Instant getReplayedAt() {
        return replayedAt;
    }

    public Integer getReplayedBy() {
        return replayedBy;
    }
}
