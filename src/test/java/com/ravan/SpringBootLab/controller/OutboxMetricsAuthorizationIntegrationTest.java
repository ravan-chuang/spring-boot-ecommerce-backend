package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.UserRepository;
import com.ravan.SpringBootLab.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.publisher.enabled=false"
})
@AutoConfigureMockMvc
class OutboxMetricsAuthorizationIntegrationTest extends TestcontainersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldRejectOutboxMetricsForNormalUser() throws Exception {
        String userToken = createUserToken("USER");

        mockMvc.perform(get("/actuator/metrics/outbox.events")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowOutboxMetricsForAdmin() throws Exception {
        String adminToken = createUserToken("ADMIN");

        mockMvc.perform(get("/actuator/metrics/outbox.events")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("outbox.events"));

        mockMvc.perform(get("/actuator/metrics/outbox.publish.success")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("outbox.publish.success"));
    }

    private String createUserToken(String role) {
        String email = role.toLowerCase()
                + "-metrics-"
                + UUID.randomUUID()
                + "@example.com";

        User user = new User(
                role + " Metrics Test User",
                email,
                "Java Backend",
                passwordEncoder.encode("password123"),
                role
        );

        return jwtService.generateToken(userRepository.save(user));
    }
}
