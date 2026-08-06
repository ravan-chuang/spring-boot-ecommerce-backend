package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.DeadLetterActionRequest;
import com.ravan.SpringBootLab.dto.DeadLetterAuditResponse;
import com.ravan.SpringBootLab.dto.DeadLetterEventResponse;
import com.ravan.SpringBootLab.dto.PageResponse;
import com.ravan.SpringBootLab.model.DeadLetterStatus;
import com.ravan.SpringBootLab.service.DeadLetterOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Dead-letter Admin API", description = "ADMIN DLT operations")
public class DeadLetterAdminController {

    private final DeadLetterOperationsService deadLetterOperationsService;

    public DeadLetterAdminController(
            DeadLetterOperationsService deadLetterOperationsService
    ) {
        this.deadLetterOperationsService = deadLetterOperationsService;
    }

    @Operation(summary = "List persisted dead-letter events")
    @GetMapping("/api/admin/dlt/events")
    public ResponseEntity<ApiResponse<PageResponse<DeadLetterEventResponse>>> findEvents(
            @RequestParam(required = false) DeadLetterStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("receivedAt").descending()
        );

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Dead-letter events retrieved successfully",
                        deadLetterOperationsService.findEvents(status, pageable)
                )
        );
    }

    @Operation(summary = "Inspect one persisted dead-letter event")
    @GetMapping("/api/admin/dlt/events/{eventId}")
    public ResponseEntity<ApiResponse<DeadLetterEventResponse>> getEvent(
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Dead-letter event retrieved successfully",
                        deadLetterOperationsService.getEvent(eventId)
                )
        );
    }

    @Operation(summary = "Get the operator audit history for a dead-letter event")
    @GetMapping("/api/admin/dlt/events/{eventId}/audit")
    public ResponseEntity<ApiResponse<List<DeadLetterAuditResponse>>> getAuditHistory(
            @PathVariable UUID eventId
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Dead-letter audit history retrieved successfully",
                        deadLetterOperationsService.getAuditHistory(eventId)
                )
        );
    }

    @Operation(summary = "Quarantine a dead-letter event after operator review")
    @PostMapping("/api/admin/dlt/events/{eventId}/quarantine")
    public ResponseEntity<ApiResponse<DeadLetterEventResponse>> quarantine(
            @PathVariable UUID eventId,
            @Valid @RequestBody DeadLetterActionRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Dead-letter event quarantined successfully",
                        deadLetterOperationsService.quarantine(
                                eventId,
                                request.getReason()
                        )
                )
        );
    }

    @Operation(summary = "Replay a quarantined dead-letter event")
    @PostMapping("/api/admin/dlt/events/{eventId}/replay")
    public ResponseEntity<ApiResponse<DeadLetterEventResponse>> replay(
            @PathVariable UUID eventId,
            @Valid @RequestBody DeadLetterActionRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Dead-letter event replayed successfully",
                        deadLetterOperationsService.replay(
                                eventId,
                                request.getReason()
                        )
                )
        );
    }
}
