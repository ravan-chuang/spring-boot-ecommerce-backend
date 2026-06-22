package com.ravan.SpringBootLab.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.publisher.enabled=false"
})
@AutoConfigureMockMvc
class AuthAuditIntegrationTest extends TestcontainersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void clearAuditLogs() {
        jdbcTemplate.update("DELETE FROM auth_audit_logs");
    }

    @Test
    void shouldRecordSuccessfulLoginAuditAndMetric() throws Exception {
        String email = "audit-login-success-" + UUID.randomUUID() + "@example.com";
        register(email, "password123");

        double before = meterRegistry.counter(
                "auth.events",
                "action", "login",
                "outcome", "success"
        ).count();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Audit Test Browser")
                        .content(loginJson(email, "password123")))
                .andExpect(status().isOk());

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_audit_logs
                WHERE event_type = 'login'
                  AND outcome = 'SUCCESS'
                """,
                Integer.class
        );

        double after = meterRegistry.counter(
                "auth.events",
                "action", "login",
                "outcome", "success"
        ).count();

        assertEquals(1, auditCount);
        assertEquals(before + 1, after);
    }

    @Test
    void shouldPersistFailedLoginAuditAfterUnauthorizedResponse() throws Exception {
        String email = "audit-login-failure-" + UUID.randomUUID() + "@example.com";
        register(email, "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Audit Failure Browser")
                        .content(loginJson(email, "wrong-password")))
                .andExpect(status().isUnauthorized());

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_audit_logs
                WHERE event_type = 'login'
                  AND outcome = 'FAILURE'
                  AND details = 'Invalid credentials'
                """,
                Integer.class
        );

        assertEquals(1, auditCount);
    }

    @Test
    void shouldRecordSingleSessionRevokeAudit() throws Exception {
        String email = "audit-session-revoke-" + UUID.randomUUID() + "@example.com";

        TokenPair firstSession = registerAndGetTokens(email, "password123");
        loginAndGetTokens(email, "password123");

        MvcResult sessionsResult = mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", bearer(firstSession.accessToken())))
                .andExpect(status().isOk())
                .andReturn();

        String sessionId = objectMapper
                .readTree(sessionsResult.getResponse().getContentAsString())
                .path("data")
                .get(0)
                .path("sessionId")
                .asText();

        mockMvc.perform(delete("/api/auth/sessions/{sessionId}", sessionId)
                        .header("Authorization", bearer(firstSession.accessToken()))
                        .header("User-Agent", "Audit Session Revoke Browser"))
                .andExpect(status().isOk());

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_audit_logs
                WHERE event_type = 'session_revoke'
                  AND outcome = 'SUCCESS'
                """,
                Integer.class
        );

        assertEquals(1, auditCount);
    }

    @Test
    void shouldRecordLogoutAllSessionsAudit() throws Exception {
        String email = "audit-logout-all-" + UUID.randomUUID() + "@example.com";

        TokenPair firstSession = registerAndGetTokens(email, "password123");
        loginAndGetTokens(email, "password123");

        mockMvc.perform(post("/api/auth/sessions/logout-all")
                        .header("Authorization", bearer(firstSession.accessToken()))
                        .header("User-Agent", "Audit Logout All Browser"))
                .andExpect(status().isOk());

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_audit_logs
                WHERE event_type = 'sessions_revoke_all'
                  AND outcome = 'SUCCESS'
                """,
                Integer.class
        );

        assertEquals(1, auditCount);
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password)))
                .andExpect(status().isOk());
    }

    private TokenPair registerAndGetTokens(
            String email,
            String password
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Audit Device A")
                        .content(registerJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return extractTokens(result);
    }

    private TokenPair loginAndGetTokens(
            String email,
            String password
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Audit Device B")
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return extractTokens(result);
    }

    private TokenPair extractTokens(MvcResult result) throws Exception {
        JsonNode data = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data");

        return new TokenPair(
                data.path("accessToken").asText(),
                data.path("refreshToken").asText()
        );
    }

    private String registerJson(String email, String password) {
        return """
                {
                  "name": "Auth Audit Test User",
                  "email": "%s",
                  "password": "%s",
                  "skill": "Java Backend"
                }
                """.formatted(email, password);
    }

    private String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TokenPair(
            String accessToken,
            String refreshToken
    ) {
    }
}
