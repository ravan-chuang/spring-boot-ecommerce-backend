package com.ravan.SpringBootLab.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
}
