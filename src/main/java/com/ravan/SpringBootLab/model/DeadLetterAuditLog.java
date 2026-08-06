package com.ravan.SpringBootLab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_audit_logs")
public class DeadLetterAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dead_letter_event_id", nullable = false)
    private UUID deadLetterEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeadLetterAuditAction action;

    @Column(name = "actor_user_id")
    private Integer actorUserId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DeadLetterAuditLog() {
    }

    public DeadLetterAuditLog(
            UUID deadLetterEventId,
            DeadLetterAuditAction action,
            Integer actorUserId,
            String actorEmail,
            String details
    ) {
        this.deadLetterEventId = deadLetterEventId;
        this.action = action;
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getDeadLetterEventId() {
        return deadLetterEventId;
    }

    public DeadLetterAuditAction getAction() {
        return action;
    }

    public Integer getActorUserId() {
        return actorUserId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
