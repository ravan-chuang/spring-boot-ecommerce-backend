package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByAggregateTypeAndAggregateIdAndEventType(
            String aggregateType,
            String aggregateId,
            String eventType
    );

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
            OutboxEventStatus status
    );

    Optional<OutboxEvent> findByIdAndStatusAndProcessingBy(
            UUID id,
            OutboxEventStatus status,
            String processingBy
    );

    @Query(
            value = """
                    SELECT *
                    FROM outbox_events
                    WHERE status = 'PENDING'
                    ORDER BY created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<OutboxEvent> findNextPendingEventsForClaim(
            @Param("limit") int limit
    );

    @Modifying
    @Query("""
            UPDATE OutboxEvent event
            SET event.status = :pendingStatus,
                event.processingAt = null,
                event.processingBy = null
            WHERE event.status = :processingStatus
              AND event.processingAt < :expiredBefore
            """)
    int recoverExpiredProcessingEvents(
            @Param("pendingStatus") OutboxEventStatus pendingStatus,
            @Param("processingStatus") OutboxEventStatus processingStatus,
            @Param("expiredBefore") LocalDateTime expiredBefore
    );
}
