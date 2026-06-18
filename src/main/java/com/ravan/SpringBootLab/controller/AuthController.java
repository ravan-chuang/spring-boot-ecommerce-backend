package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.AuthResponse;
import com.ravan.SpringBootLab.dto.LoginRequest;
import com.ravan.SpringBootLab.dto.RegisterRequest;
import com.ravan.SpringBootLab.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth API", description = "Register and login APIs")
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register", description = "Register a new user and return JWT token")
    @PostMapping("/api/auth/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response = authService.register(request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Register successfully",
                        response
                )
        );
    }

    @Operation(summary = "Login", description = "Login and return JWT token")
    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authService.login(request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Login successfully",
                        response
                )
        );
    }
}
