package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import com.ravan.SpringBootLab.repository.UserRepository;
import com.ravan.SpringBootLab.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.publisher.enabled=false"
})
@AutoConfigureMockMvc
class OutboxAdminControllerIntegrationTest extends TestcontainersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void clearOutboxEvents() {
        outboxEventRepository.deleteAllInBatch();
    }

    @Test
    void shouldRejectFailedOutboxQueryForNormalUser() throws Exception {
        String userToken = createUserToken("USER");

        mockMvc.perform(get("/api/admin/outbox/failed")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReplayFailedOutboxEventForAdmin() throws Exception {
        OutboxEvent event = new OutboxEvent(
                "ORDER",
                "outbox-admin-test-" + UUID.randomUUID(),
                "ORDER_CREATED",
                KafkaTopicConfig.ORDER_CREATED_TOPIC,
                "{\"event\":\"outbox-admin-test\"}"
        );

        event.markFailed("Kafka broker unavailable");
        OutboxEvent failedEvent = outboxEventRepository.saveAndFlush(event);

        String adminToken = createUserToken("ADMIN");

        mockMvc.perform(post("/api/admin/outbox/{eventId}/replay", failedEvent.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.retryCount").value(0))
                .andExpect(jsonPath("$.data.lastError").doesNotExist());

        OutboxEvent replayedEvent = outboxEventRepository
                .findById(failedEvent.getId())
                .orElseThrow();

        assertEquals(OutboxEventStatus.PENDING, replayedEvent.getStatus());
        assertEquals(0, replayedEvent.getRetryCount());
        assertNull(replayedEvent.getLastError());
    }

    private String createUserToken(String role) {
        String email = role.toLowerCase() + "-outbox-" + UUID.randomUUID() + "@example.com";

        User user = new User(
                role + " Outbox Admin Test User",
                email,
                "Java Backend",
                passwordEncoder.encode("password123"),
                role
        );

        User savedUser = userRepository.save(user);
        return jwtService.generateToken(savedUser);
    }
}
