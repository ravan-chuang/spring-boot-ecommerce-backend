package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.AuthResponse;
import com.ravan.SpringBootLab.dto.AuthSessionResponse;
import com.ravan.SpringBootLab.dto.LoginRequest;
import com.ravan.SpringBootLab.dto.LogoutRequest;
import com.ravan.SpringBootLab.dto.RefreshTokenRequest;
import com.ravan.SpringBootLab.dto.RegisterRequest;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.UserRepository;
import com.ravan.SpringBootLab.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthAuditService authAuditService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuthAuditService authAuditService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authAuditService = authAuditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return register(request, "Unknown device", null);
    }

    @Transactional
    public AuthResponse register(
            RegisterRequest request,
            String deviceName,
            String ipAddress
    ) {
        if (userRepository.existsByEmail(request.getEmail())) {
            authAuditService.recordFailure(
                    "register",
                    null,
                    ipAddress,
                    deviceName,
                    "Email already registered"
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email already registered"
            );
        }

        User savedUser = userRepository.save(
                new User(
                        request.getName(),
                        request.getEmail(),
                        request.getSkill(),
                        passwordEncoder.encode(request.getPassword()),
                        "USER"
                )
        );

        AuthResponse response = createAuthResponse(
                savedUser,
                refreshTokenService.issue(savedUser, deviceName, ipAddress)
        );

        authAuditService.recordSuccess(
                "register",
                savedUser,
                ipAddress,
                deviceName,
                "User registered"
        );

        return response;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, "Unknown device", null);
    }

    @Transactional
    public AuthResponse login(
            LoginRequest request,
            String deviceName,
            String ipAddress
    ) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(
                        request.getPassword(),
                        user.getPasswordHash()
                )) {
            authAuditService.recordFailure(
                    "login",
                    user,
                    ipAddress,
                    deviceName,
                    "Invalid credentials"
            );

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        AuthResponse response = createAuthResponse(
                user,
                refreshTokenService.issue(user, deviceName, ipAddress)
        );

        authAuditService.recordSuccess(
                "login",
                user,
                ipAddress,
                deviceName,
                "Login succeeded"
        );

        return response;
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        return refresh(request, null, null);
    }

    @Transactional
    public AuthResponse refresh(
            RefreshTokenRequest request,
            String deviceName,
            String ipAddress
    ) {
        try {
            RefreshTokenService.RefreshTokenRotation rotation =
                    refreshTokenService.rotate(
                            request.refreshToken(),
                            deviceName,
                            ipAddress
                    );

            AuthResponse response = createAuthResponse(
                    rotation.user(),
                    rotation.refreshToken()
            );

            authAuditService.recordSuccess(
                    "refresh",
                    rotation.user(),
                    ipAddress,
                    deviceName,
                    "Refresh token rotated"
            );

            return response;
        } catch (ResponseStatusException exception) {
            authAuditService.recordFailure(
                    "refresh",
                    null,
                    ipAddress,
                    deviceName,
                    exception.getReason()
            );

            throw exception;
        }
    }

    @Transactional
    public void logout(LogoutRequest request) {
        logout(request, null, null);
    }

    @Transactional
    public void logout(
            LogoutRequest request,
            String deviceName,
            String ipAddress
    ) {
        try {
            User user = refreshTokenService.revoke(request.refreshToken());

            authAuditService.recordSuccess(
                    "logout",
                    user,
                    ipAddress,
                    deviceName,
                    "Refresh token revoked"
            );
        } catch (ResponseStatusException exception) {
            authAuditService.recordFailure(
                    "logout",
                    null,
                    ipAddress,
                    deviceName,
                    exception.getReason()
            );

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<AuthSessionResponse> listActiveSessions(User user) {
        return refreshTokenService.listActiveSessions(user);
    }

    @Transactional
    public void revokeSession(
            User user,
            UUID sessionId,
            String deviceName,
            String ipAddress
    ) {
        try {
            refreshTokenService.revokeSession(user, sessionId);

            authAuditService.recordSuccess(
                    "session_revoke",
                    user,
                    ipAddress,
                    deviceName,
                    "Session revoked: " + sessionId
            );
        } catch (ResponseStatusException exception) {
            authAuditService.recordFailure(
                    "session_revoke",
                    user,
                    ipAddress,
                    deviceName,
                    exception.getReason()
            );

            throw exception;
        }
    }

    @Transactional
    public int revokeAllSessions(
            User user,
            String deviceName,
            String ipAddress
    ) {
        int revokedCount = refreshTokenService.revokeAllSessions(user);

        authAuditService.recordSuccess(
                "sessions_revoke_all",
                user,
                ipAddress,
                deviceName,
                "Revoked active sessions: " + revokedCount
        );

        return revokedCount;
    }

    private AuthResponse createAuthResponse(
            User user,
            String refreshToken
    ) {
        return new AuthResponse(
                jwtService.generateToken(user),
                refreshToken,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
