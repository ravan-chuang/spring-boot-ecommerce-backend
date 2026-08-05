package com.ravan.SpringBootLab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_idempotency_records_key_path",
                columnNames = {"idempotency_key", "request_path"}
        )
)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 255)
    private String idempotencyKey;

    @Column(nullable = false, length = 255)
    private String requestPath;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    @Column(nullable = false)
    private Integer paymentId;

    @Column(nullable = false)
    private Integer responseStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(
            String idempotencyKey,
            String requestPath,
            String requestFingerprint,
            Integer paymentId,
            Integer responseStatus,
            LocalDateTime expiresAt
    ) {
        this.idempotencyKey = idempotencyKey;
        this.requestPath = requestPath;
        this.requestFingerprint = requestFingerprint;
        this.paymentId = paymentId;
        this.responseStatus = responseStatus;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    public Integer getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Integer getPaymentId() {
        return paymentId;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public void setPaymentId(Integer paymentId) {
        this.paymentId = paymentId;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
