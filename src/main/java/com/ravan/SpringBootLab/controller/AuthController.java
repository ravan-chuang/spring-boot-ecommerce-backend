package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.AuthResponse;
import com.ravan.SpringBootLab.dto.LoginRequest;
import com.ravan.SpringBootLab.dto.LogoutRequest;
import com.ravan.SpringBootLab.dto.RefreshTokenRequest;
import com.ravan.SpringBootLab.dto.RegisterRequest;
import com.ravan.SpringBootLab.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth API", description = "Register, login, refresh-token, and logout APIs")
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register", description = "Register a new user and return access and refresh tokens")
    @PostMapping("/api/auth/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Register successfully",
                        authService.register(request)
                )
        );
    }

    @Operation(summary = "Login", description = "Login and return access and refresh tokens")
    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Login successfully",
                        authService.login(request)
                )
        );
    }

    @Operation(
            summary = "Refresh access token",
            description = "Rotate the refresh token and return a new access token and refresh token"
    )
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Token refreshed successfully",
                        authService.refresh(request)
                )
        );
    }

    @Operation(
            summary = "Logout",
            description = "Revoke the supplied refresh token"
    )
    @PostMapping("/api/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Logout successfully",
                        null
                )
        );
    }
}
