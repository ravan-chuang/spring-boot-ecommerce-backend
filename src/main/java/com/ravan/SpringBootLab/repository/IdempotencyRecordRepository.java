package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Integer> {

    Optional<IdempotencyRecord> findByIdempotencyKeyAndRequestPath(
            String idempotencyKey,
            String requestPath
    );
}