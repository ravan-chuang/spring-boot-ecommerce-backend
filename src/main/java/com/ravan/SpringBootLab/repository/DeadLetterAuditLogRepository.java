package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.DeadLetterAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeadLetterAuditLogRepository extends JpaRepository<DeadLetterAuditLog, Long> {

    List<DeadLetterAuditLog> findByDeadLetterEventIdOrderByCreatedAtAsc(
            UUID deadLetterEventId
    );
}
