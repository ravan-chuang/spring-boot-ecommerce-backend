package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.dto.ApiResponse;
import com.ravan.SpringBootLab.dto.AuthResponse;
import com.ravan.SpringBootLab.dto.AuthSessionResponse;
import com.ravan.SpringBootLab.dto.LoginRequest;
import com.ravan.SpringBootLab.dto.LogoutAllSessionsResponse;
import com.ravan.SpringBootLab.dto.LogoutRequest;
import com.ravan.SpringBootLab.dto.RefreshTokenRequest;
import com.ravan.SpringBootLab.dto.RegisterRequest;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.security.CurrentUserService;
import com.ravan.SpringBootLab.service.AuthService;
import com.ravan.SpringBootLab.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Auth API",
        description = "Register, login, refresh-token, logout, and session management APIs"
)
@RestController
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentUserService currentUserService;

    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            CurrentUserService currentUserService
    ) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.currentUserService = currentUserService;
    }

    @Operation(
            summary = "Register",
            description = "Register a new user and return access and refresh tokens"
    )
    @PostMapping("/api/auth/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Register successfully",
                        authService.register(
                                request,
                                deviceName(httpRequest),
                                clientIp(httpRequest)
                        )
                )
        );
    }

    @Operation(
            summary = "Login",
            description = "Login and return access and refresh tokens"
    )
    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Login successfully",
                        authService.login(
                                request,
                                deviceName(httpRequest),
                                clientIp(httpRequest)
                        )
                )
        );
    }

    @Operation(
            summary = "Refresh access token",
            description = "Rotate the refresh token and return a new access token and refresh token"
    )
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Token refreshed successfully",
                        authService.refresh(
                                request,
                                deviceName(httpRequest),
                                clientIp(httpRequest)
                        )
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

    @Operation(
            summary = "List active sessions",
            description = "Return active refresh-token sessions for the current user"
    )
    @GetMapping("/api/auth/sessions")
    public ResponseEntity<ApiResponse<List<AuthSessionResponse>>> listSessions() {
        User currentUser = currentUserService.getCurrentUser();

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Active sessions retrieved successfully",
                        refreshTokenService.listActiveSessions(currentUser)
                )
        );
    }

    @Operation(
            summary = "Logout a session",
            description = "Revoke one active refresh-token session owned by the current user"
    )
    @DeleteMapping("/api/auth/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> logoutSession(
            @PathVariable UUID sessionId
    ) {
        User currentUser = currentUserService.getCurrentUser();
        refreshTokenService.revokeSession(currentUser, sessionId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "Session logged out successfully",
                        null
                )
        );
    }

    @Operation(
            summary = "Logout all sessions",
            description = "Revoke all active refresh-token sessions for the current user"
    )
    @PostMapping("/api/auth/sessions/logout-all")
    public ResponseEntity<ApiResponse<LogoutAllSessionsResponse>> logoutAllSessions() {
        User currentUser = currentUserService.getCurrentUser();

        int revokedSessionCount =
                refreshTokenService.revokeAllSessions(currentUser);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        200,
                        "All sessions logged out successfully",
                        new LogoutAllSessionsResponse(revokedSessionCount)
                )
        );
    }

    private String deviceName(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");

        return userAgent == null || userAgent.isBlank()
                ? "Unknown device"
                : userAgent;
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
