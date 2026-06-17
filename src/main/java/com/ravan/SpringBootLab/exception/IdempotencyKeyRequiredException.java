package com.ravan.SpringBootLab.exception;

public class IdempotencyKeyRequiredException extends RuntimeException {

    public IdempotencyKeyRequiredException() {
        super("Idempotency-Key header is required");
    }
}