package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.AuthSessionResponse;
import com.ravan.SpringBootLab.model.RefreshToken;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final long expirationDays;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${refresh-token.expiration-days:30}") long expirationDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationDays = expirationDays;
    }

    @Transactional
    public String issue(User user) {
        return issue(user, "Unknown device", null);
    }

    @Transactional
    public String issue(
            User user,
            String deviceName,
            String ipAddress
    ) {
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken(
                user,
                hash(rawToken),
                expiresAt(),
                UUID.randomUUID(),
                normalizeDeviceName(deviceName),
                normalizeIpAddress(ipAddress)
        );

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Transactional
    public RefreshTokenRotation rotate(String rawToken) {
        return rotate(rawToken, null, null);
    }

    @Transactional
    public RefreshTokenRotation rotate(
            String rawToken,
            String deviceName,
            String ipAddress
    ) {
        RefreshToken currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(this::invalidRefreshToken);

        if (!currentToken.isActive(Instant.now())) {
            throw invalidRefreshToken();
        }

        String newRawToken = generateRawToken();

        RefreshToken replacementToken = new RefreshToken(
                currentToken.getUser(),
                hash(newRawToken),
                expiresAt(),
                currentToken.getSessionId(),
                hasText(deviceName)
                        ? normalizeDeviceName(deviceName)
                        : currentToken.getDeviceName(),
                hasText(ipAddress)
                        ? normalizeIpAddress(ipAddress)
                        : currentToken.getIpAddress()
        );

        refreshTokenRepository.save(replacementToken);

        currentToken.markUsed();
        currentToken.revoke(replacementToken.getId());
        refreshTokenRepository.save(currentToken);

        return new RefreshTokenRotation(
                currentToken.getUser(),
                newRawToken
        );
    }

    @Transactional
    public void revoke(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(this::invalidRefreshToken);

        if (refreshToken.getRevokedAt() == null) {
            refreshToken.revoke(null);
            refreshTokenRepository.save(refreshToken);
        }
    }

    @Transactional(readOnly = true)
    public List<AuthSessionResponse> listActiveSessions(User user) {
        return refreshTokenRepository.findActiveByUserId(user.getId())
                .stream()
                .map(token -> new AuthSessionResponse(
                        token.getSessionId(),
                        token.getDeviceName(),
                        token.getIpAddress(),
                        token.getCreatedAt(),
                        token.getLastUsedAt(),
                        token.getExpiresAt()
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(User user, UUID sessionId) {
        int revokedCount = refreshTokenRepository.revokeActiveSession(
                user.getId(),
                sessionId,
                Instant.now()
        );

        if (revokedCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Active session not found"
            );
        }
    }

    @Transactional
    public int revokeAllSessions(User user) {
        return refreshTokenRepository.revokeAllActiveSessions(
                user.getId(),
                Instant.now()
        );
    }

    private Instant expiresAt() {
        return Instant.now().plusSeconds(expirationDays * 24 * 60 * 60);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();

            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }

            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }

    private String normalizeDeviceName(String deviceName) {
        if (!hasText(deviceName)) {
            return "Unknown device";
        }

        return deviceName.trim()
                .substring(0, Math.min(deviceName.trim().length(), 255));
    }

    private String normalizeIpAddress(String ipAddress) {
        if (!hasText(ipAddress)) {
            return null;
        }

        return ipAddress.trim()
                .substring(0, Math.min(ipAddress.trim().length(), 64));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid or expired refresh token"
        );
    }

    public record RefreshTokenRotation(
            User user,
            String refreshToken
    ) {
    }
}
