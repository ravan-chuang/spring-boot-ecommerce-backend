package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthAuditService {

    private static final Logger logger =
            LoggerFactory.getLogger(AuthAuditService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AuthMetrics authMetrics;

    public AuthAuditService(
            JdbcTemplate jdbcTemplate,
            AuthMetrics authMetrics
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.authMetrics = authMetrics;
    }

    public void recordSuccess(
            String action,
            User user,
            String ipAddress,
            String deviceName,
            String details
    ) {
        record(
                action,
                "SUCCESS",
                user,
                ipAddress,
                deviceName,
                details
        );

        authMetrics.recordSuccess(action);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String action,
            User user,
            String ipAddress,
            String deviceName,
            String details
    ) {
        record(
                action,
                "FAILURE",
                user,
                ipAddress,
                deviceName,
                details
        );

        authMetrics.recordFailure(action);
    }

    private void record(
            String action,
            String outcome,
            User user,
            String ipAddress,
            String deviceName,
            String details
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO auth_audit_logs (
                    user_id,
                    event_type,
                    outcome,
                    ip_address,
                    device_name,
                    details,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                user == null ? null : user.getId(),
                action,
                outcome,
                normalize(ipAddress, 64),
                normalize(deviceName, 255),
                normalize(details, 500)
        );

        logger.info(
                "Auth audit recorded: action={}, outcome={}, userId={}, ipAddress={}",
                action,
                outcome,
                user == null ? null : user.getId(),
                ipAddress
        );
    }

    private String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.substring(0, Math.min(trimmed.length(), maxLength));
    }
}
