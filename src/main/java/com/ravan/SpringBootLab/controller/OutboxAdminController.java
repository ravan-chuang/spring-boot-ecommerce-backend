package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.OutboxEventResponse;
import com.ravan.SpringBootLab.service.OutboxAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Outbox Admin API", description = "ADMIN APIs for failed outbox events")
public class OutboxAdminController {

    private final OutboxAdminService outboxAdminService;

    public OutboxAdminController(OutboxAdminService outboxAdminService) {
        this.outboxAdminService = outboxAdminService;
    }

    @Operation(
            summary = "Get failed outbox events",
            description = "Returns all FAILED outbox events. ADMIN only."
    )
    @GetMapping("/api/admin/outbox/failed")
    public ResponseEntity<ApiResponse<List<OutboxEventResponse>>> getFailedEvents() {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Failed outbox events retrieved successfully",
                        outboxAdminService.getFailedEvents()
                )
        );
    }

    @Operation(
            summary = "Replay a failed outbox event",
            description = "Resets a FAILED event to PENDING so the publisher can retry it. ADMIN only."
    )
    @PostMapping("/api/admin/outbox/{eventId}/replay")
    public ResponseEntity<ApiResponse<OutboxEventResponse>> replayFailedEvent(
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Outbox event replay scheduled successfully",
                        outboxAdminService.replayFailedEvent(eventId)
                )
        );
    }
}
