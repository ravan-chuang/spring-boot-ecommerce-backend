package com.ravan.SpringBootLab.service;

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
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken(
                user,
                hash(rawToken),
                Instant.now().plusSeconds(expirationDays * 24 * 60 * 60)
        );

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Transactional
    public RefreshTokenRotation rotate(String rawToken) {
        RefreshToken currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(this::invalidRefreshToken);

        if (!currentToken.isActive(Instant.now())) {
            throw invalidRefreshToken();
        }

        User user = currentToken.getUser();

        String newRawToken = generateRawToken();
        RefreshToken replacementToken = new RefreshToken(
                user,
                hash(newRawToken),
                Instant.now().plusSeconds(expirationDays * 24 * 60 * 60)
        );

        refreshTokenRepository.save(replacementToken);

        currentToken.revoke(replacementToken.getId());
        refreshTokenRepository.save(currentToken);

        return new RefreshTokenRotation(user, newRawToken);
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
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
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
