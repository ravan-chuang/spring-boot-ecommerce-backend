package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT refreshToken
            FROM RefreshToken refreshToken
            JOIN FETCH refreshToken.user
            WHERE refreshToken.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Query("""
            SELECT refreshToken
            FROM RefreshToken refreshToken
            WHERE refreshToken.user.id = :userId
              AND refreshToken.revokedAt IS NULL
            ORDER BY refreshToken.lastUsedAt DESC
            """)
    List<RefreshToken> findActiveByUserId(
            @Param("userId") Integer userId
    );

    @Modifying
    @Query("""
            UPDATE RefreshToken refreshToken
            SET refreshToken.revokedAt = :revokedAt
            WHERE refreshToken.user.id = :userId
              AND refreshToken.sessionId = :sessionId
              AND refreshToken.revokedAt IS NULL
            """)
    int revokeActiveSession(
            @Param("userId") Integer userId,
            @Param("sessionId") UUID sessionId,
            @Param("revokedAt") Instant revokedAt
    );

    @Modifying
    @Query("""
            UPDATE RefreshToken refreshToken
            SET refreshToken.revokedAt = :revokedAt
            WHERE refreshToken.user.id = :userId
              AND refreshToken.revokedAt IS NULL
            """)
    int revokeAllActiveSessions(
            @Param("userId") Integer userId,
            @Param("revokedAt") Instant revokedAt
    );
}
