package com.ravan.SpringBootLab.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends TestcontainersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        String email = "register-test-" + UUID.randomUUID() + "@example.com";

        String registerJson = """
                {
                  "name": "Register Test User",
                  "email": "%s",
                  "password": "password123",
                  "skill": "Java Backend"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Register successfully"))
                .andExpect(jsonPath("$.data.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        String email = "login-test-" + UUID.randomUUID() + "@example.com";

        registerUser(email, "password123");

        String loginJson = """
                {
                  "email": "%s",
                  "password": "password123"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successfully"))
                .andExpect(jsonPath("$.data.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        String email = "duplicate-test-" + UUID.randomUUID() + "@example.com";

        registerUser(email, "password123");

        String duplicateRegisterJson = """
                {
                  "name": "Duplicate Test User",
                  "email": "%s",
                  "password": "password123",
                  "skill": "Java Backend"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateRegisterJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        String email = "wrong-password-test-" + UUID.randomUUID() + "@example.com";

        registerUser(email, "password123");

        String loginJson = """
                {
                  "email": "%s",
                  "password": "wrongpassword"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRotateRefreshTokenAndRejectOldRefreshToken() throws Exception {
        String email = "refresh-rotation-" + UUID.randomUUID() + "@example.com";
        TokenPair originalTokens = registerAndGetTokens(email, "password123");

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(originalTokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Token refreshed successfully"))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andReturn();

        TokenPair rotatedTokens = extractTokens(refreshResult);

        assertNotEquals(
                originalTokens.refreshToken(),
                rotatedTokens.refreshToken()
        );

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(originalTokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRefreshTokenAfterLogout() throws Exception {
        String email = "logout-test-" + UUID.randomUUID() + "@example.com";
        TokenPair tokens = registerAndGetTokens(email, "password123");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(tokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successfully"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldListAndRevokeOneOwnedSession() throws Exception {
        String email = "session-test-" + UUID.randomUUID() + "@example.com";

        TokenPair firstDevice = registerAndGetTokens(email, "password123");
        TokenPair secondDevice = loginAndGetTokens(email, "password123");

        MvcResult sessionsResult = mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", bearer(firstDevice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Active sessions retrieved successfully"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andReturn();

        JsonNode sessions = objectMapper
                .readTree(sessionsResult.getResponse().getContentAsString())
                .path("data");

        String sessionIdToRevoke = sessions.get(0).path("sessionId").asText();

        mockMvc.perform(delete("/api/auth/sessions/{sessionId}", sessionIdToRevoke)
                        .header("Authorization", bearer(firstDevice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Session logged out successfully"));

        mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", bearer(secondDevice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void shouldRevokeAllSessionsForCurrentUser() throws Exception {
        String email = "logout-all-test-" + UUID.randomUUID() + "@example.com";

        TokenPair firstDevice = registerAndGetTokens(email, "password123");
        TokenPair secondDevice = loginAndGetTokens(email, "password123");

        mockMvc.perform(post("/api/auth/sessions/logout-all")
                        .header("Authorization", bearer(firstDevice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("All sessions logged out successfully"))
                .andExpect(jsonPath("$.data.revokedSessionCount").value(2));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(firstDevice.refreshToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(secondDevice.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    private void registerUser(String email, String password) throws Exception {
        String registerJson = """
                {
                  "name": "Auth Test User",
                  "email": "%s",
                  "password": "%s",
                  "skill": "Java Backend"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isOk());
    }

    private TokenPair registerAndGetTokens(
            String email,
            String password
    ) throws Exception {
        String registerJson = """
                {
                  "name": "Refresh Token Test User",
                  "email": "%s",
                  "password": "%s",
                  "skill": "Java Backend"
                }
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Test Browser / Device A")
                        .content(registerJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andReturn();

        return extractTokens(result);
    }

    private TokenPair loginAndGetTokens(
            String email,
            String password
    ) throws Exception {
        String loginJson = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Test Browser / Device B")
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
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

    private String refreshRequest(String refreshToken) {
        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);
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
