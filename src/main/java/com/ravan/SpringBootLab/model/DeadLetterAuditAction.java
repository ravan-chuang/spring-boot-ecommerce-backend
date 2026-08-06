package com.ravan.SpringBootLab.model;

public enum DeadLetterAuditAction {
    CAPTURED,
    QUARANTINED,
    REPLAY_REQUESTED,
    REPLAY_SUCCEEDED,
    REPLAY_FAILED,
    REPLAY_LEASE_RECOVERED
}
