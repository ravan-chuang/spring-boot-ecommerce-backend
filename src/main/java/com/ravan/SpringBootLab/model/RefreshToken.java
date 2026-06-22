package com.ravan.SpringBootLab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "device_name", nullable = false, length = 255)
    private String deviceName;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    public RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this(
                user,
                tokenHash,
                expiresAt,
                UUID.randomUUID(),
                "Unknown device",
                null
        );
    }

    public RefreshToken(
            User user,
            String tokenHash,
            Instant expiresAt,
            UUID sessionId,
            String deviceName,
            String ipAddress
    ) {
        Instant now = Instant.now();

        this.id = UUID.randomUUID();
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.sessionId = sessionId;
        this.deviceName = deviceName;
        this.ipAddress = ipAddress;
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }

    public void revoke(UUID replacementTokenId) {
        this.revokedAt = Instant.now();
        this.replacedById = replacementTokenId;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
