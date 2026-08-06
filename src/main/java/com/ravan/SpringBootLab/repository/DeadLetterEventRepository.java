package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.DeadLetterEvent;
import com.ravan.SpringBootLab.model.DeadLetterStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {

    Optional<DeadLetterEvent> findByDltTopicAndDltPartitionAndDltOffset(
            String dltTopic,
            Integer dltPartition,
            Long dltOffset
    );

    Optional<DeadLetterEvent> findFirstByMessageKeyOrderByReceivedAtDesc(
            String messageKey
    );

    Page<DeadLetterEvent> findByStatus(
            DeadLetterStatus status,
            Pageable pageable
    );

    long countByStatus(DeadLetterStatus status);

    List<DeadLetterEvent> findByStatusAndReplayStartedAtBefore(
            DeadLetterStatus status,
            Instant replayStartedBefore
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT event FROM DeadLetterEvent event WHERE event.id = :id")
    Optional<DeadLetterEvent> findByIdForUpdate(@Param("id") UUID id);
}
