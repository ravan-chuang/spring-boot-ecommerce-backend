package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.CreatePaymentRequest;
import com.ravan.SpringBootLab.dto.PaymentResponse;
import com.ravan.SpringBootLab.security.CurrentUserService;
import com.ravan.SpringBootLab.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Payment API", description = "Payment APIs")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    public PaymentController(
            PaymentService paymentService,
            CurrentUserService currentUserService
    ) {
        this.paymentService = paymentService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "Pay order", description = "Simulate payment for an order")
    @PostMapping("/api/orders/{orderId}/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> payOrder(
            @PathVariable Integer orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        currentUserService.requireOrderOwnerOrAdmin(orderId);

        PaymentResponse payment = paymentService.payOrder(
                orderId,
                request,
                idempotencyKey
        );

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Payment completed successfully",
                        payment
                )
        );
    }

    @Operation(summary = "Get payment by order", description = "Get payment information of an order")
    @GetMapping("/api/orders/{orderId}/payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(
            @PathVariable Integer orderId
    ) {
        currentUserService.requireOrderOwnerOrAdmin(orderId);

        PaymentResponse payment = paymentService.getPaymentByOrder(orderId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Success",
                        payment
                )
        );
    }
}
