package com.ravan.SpringBootLab.dto;

import com.ravan.SpringBootLab.model.DeadLetterAuditAction;

import java.time.Instant;

public class DeadLetterAuditResponse {

    private final Long id;
    private final DeadLetterAuditAction action;
    private final Integer actorUserId;
    private final String actorEmail;
    private final String details;
    private final Instant createdAt;

    public DeadLetterAuditResponse(
            Long id,
            DeadLetterAuditAction action,
            Integer actorUserId,
            String actorEmail,
            String details,
            Instant createdAt
    ) {
        this.id = id;
        this.action = action;
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.details = details;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public DeadLetterAuditAction getAction() { return action; }
    public Integer getActorUserId() { return actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
