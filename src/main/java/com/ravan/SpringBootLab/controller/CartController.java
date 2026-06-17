package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.AddCartItemRequest;
import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.CartItemResponse;
import com.ravan.SpringBootLab.dto.UpdateCartItemRequest;
import com.ravan.SpringBootLab.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Cart API", description = "Shopping cart APIs")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "Add item to cart", description = "Add a product to user's shopping cart")
    @PostMapping("/api/users/{userId}/cart/items")
    public ResponseEntity<ApiResponse<CartItemResponse>> addItemToCart(
            @PathVariable Integer userId,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        CartItemResponse cartItem = cartService.addItemToCart(userId, request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Item added to cart successfully",
                        cartItem
                )
        );
    }

    @Operation(summary = "Get cart items", description = "Get all cart items of a user")
    @GetMapping("/api/users/{userId}/cart/items")
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> getCartItems(
            @PathVariable Integer userId
    ) {
        List<CartItemResponse> cartItems = cartService.getCartItems(userId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Success",
                        cartItems
                )
        );
    }

    @Operation(summary = "Update cart item", description = "Update quantity of a cart item")
    @PutMapping("/api/users/{userId}/cart/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartItemResponse>> updateCartItem(
            @PathVariable Integer userId,
            @PathVariable Integer cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        CartItemResponse cartItem = cartService.updateCartItem(
                userId,
                cartItemId,
                request
        );

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Cart item updated successfully",
                        cartItem
                )
        );
    }

    @Operation(summary = "Delete cart item", description = "Delete a cart item")
    @DeleteMapping("/api/users/{userId}/cart/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(
            @PathVariable Integer userId,
            @PathVariable Integer cartItemId
    ) {
        cartService.deleteCartItem(userId, cartItemId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Cart item deleted successfully",
                        null
                )
        );
    }
}