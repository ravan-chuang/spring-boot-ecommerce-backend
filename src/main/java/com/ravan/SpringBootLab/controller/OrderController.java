package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.OrderResponse;
import com.ravan.SpringBootLab.security.CurrentUserService;
import com.ravan.SpringBootLab.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Order API", description = "Order APIs")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public OrderController(
            OrderService orderService,
            CurrentUserService currentUserService
    ) {
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "Create order", description = "Create an order from user's cart")
    @PostMapping("/api/users/{userId}/orders")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @PathVariable Integer userId
    ) {
        currentUserService.requireUserIdOrAdmin(userId);

        OrderResponse order = orderService.createOrderFromCart(userId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Order created successfully",
                        order
                )
        );
    }

    @Operation(summary = "Get user's orders", description = "Get all orders of a user")
    @GetMapping("/api/users/{userId}/orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByUser(
            @PathVariable Integer userId
    ) {
        currentUserService.requireUserIdOrAdmin(userId);

        List<OrderResponse> orders = orderService.getOrdersByUser(userId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Success",
                        orders
                )
        );
    }

    @Operation(summary = "Get order by id", description = "Get a single order by id")
    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable Integer orderId
    ) {
        currentUserService.requireOrderOwnerOrAdmin(orderId);

        OrderResponse order = orderService.getOrderById(orderId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Success",
                        order
                )
        );
    }

    @Operation(summary = "Cancel order", description = "Cancel a pending order and restore product stock")
    @PostMapping("/api/orders/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Integer orderId
    ) {
        currentUserService.requireOrderOwnerOrAdmin(orderId);

        OrderResponse order = orderService.cancelOrder(orderId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Order cancelled successfully",
                        order
                )
        );
    }

    @Operation(summary = "Create order slowly", description = "Create an order slowly for optimistic lock testing")
    @PostMapping("/api/users/{userId}/orders/slow")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrderSlow(
            @PathVariable Integer userId
    ) {
        currentUserService.requireUserIdOrAdmin(userId);

        OrderResponse order = orderService.createOrderFromCartSlow(userId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Order created successfully",
                        order
                )
        );
    }
}
