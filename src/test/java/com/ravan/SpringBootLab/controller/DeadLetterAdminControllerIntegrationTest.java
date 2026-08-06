package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.model.DeadLetterAuditAction;
import com.ravan.SpringBootLab.model.DeadLetterEvent;
import com.ravan.SpringBootLab.model.DeadLetterStatus;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.DeadLetterAuditLogRepository;
import com.ravan.SpringBootLab.repository.DeadLetterEventRepository;
import com.ravan.SpringBootLab.repository.UserRepository;
import com.ravan.SpringBootLab.security.JwtService;
import com.ravan.SpringBootLab.service.EventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.publisher.enabled=false"
})
@AutoConfigureMockMvc
class DeadLetterAdminControllerIntegrationTest extends TestcontainersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeadLetterEventRepository eventRepository;

    @Autowired
    private DeadLetterAuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EventProducer eventProducer;

    @BeforeEach
    void clearDeadLetterRecords() {
        auditLogRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
    }

    @Test
    void normalUsersCannotAccessDeadLetterOperations() throws Exception {
        DeadLetterEvent event = saveEvent("forbidden");
        String userToken = createToken("USER");

        mockMvc.perform(get("/api/admin/dlt/events")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/dlt/events/{id}/quarantine", event.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"reviewed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanInspectQuarantineReplayAndAuditEvent() throws Exception {
        DeadLetterEvent event = saveEvent("happy-path");
        String adminToken = createToken("ADMIN");

        mockMvc.perform(get("/api/admin/dlt/events")
                        .param("status", "RECEIVED")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Correlation-ID", "admin-dlt-request"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "admin-dlt-request"))
                .andExpect(jsonPath("$.data.content[0].id")
                        .value(event.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].correlationId")
                        .value("dlt-correlation-happy-path"));

        mockMvc.perform(get("/api/admin/dlt/events/{id}", event.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"));

        mockMvc.perform(post("/api/admin/dlt/events/{id}/quarantine", event.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Payload reviewed and consumer fixed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUARANTINED"));

        mockMvc.perform(post("/api/admin/dlt/events/{id}/replay", event.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Safe to replay after deployment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REPLAYED"))
                .andExpect(jsonPath("$.data.replayAttempts").value(1));

        verify(eventProducer).send(
                eq("order-created"),
                eq(0),
                eq("happy-path"),
                eq("{\"orderId\":42}"),
                any(UUID.class),
                eq("dlt-correlation-happy-path")
        );

        mockMvc.perform(get("/api/admin/dlt/events/{id}/audit", event.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("QUARANTINED"))
                .andExpect(jsonPath("$.data[1].action").value("REPLAY_REQUESTED"))
                .andExpect(jsonPath("$.data[2].action").value("REPLAY_SUCCEEDED"));

        DeadLetterEvent replayed = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(replayed.getStatus()).isEqualTo(DeadLetterStatus.REPLAYED);
        assertThat(replayed.getReplayedBy()).isNotNull();
    }

    @Test
    void replayFailureReturnsEventToQuarantineAndWritesAudit() throws Exception {
        DeadLetterEvent event = saveEvent("send-failure");
        String adminToken = createToken("ADMIN");

        mockMvc.perform(post("/api/admin/dlt/events/{id}/quarantine", event.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Reviewed\"}"))
                .andExpect(status().isOk());

        doThrow(new RuntimeException("broker unavailable"))
                .when(eventProducer).send(
                        eq("order-created"), eq(0), eq("send-failure"),
                        any(), any(UUID.class), eq("dlt-correlation-send-failure")
                );

        mockMvc.perform(post("/api/admin/dlt/events/{id}/replay", event.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Attempt after remediation\"}"))
                .andExpect(status().isBadGateway());

        DeadLetterEvent failed = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(DeadLetterStatus.QUARANTINED);
        assertThat(auditLogRepository
                .findByDeadLetterEventIdOrderByCreatedAtAsc(event.getId()))
                .extracting(log -> log.getAction())
                .contains(DeadLetterAuditAction.REPLAY_FAILED);
    }

    @Test
    void rejectsBlankReasonAndInvalidStateTransitions() throws Exception {
        DeadLetterEvent event = saveEvent("invalid-transition");
        String adminToken = createToken("ADMIN");

        mockMvc.perform(post("/api/admin/dlt/events/{id}/quarantine", event.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/dlt/events/{id}/replay", event.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not quarantined\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/admin/dlt/events/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private DeadLetterEvent saveEvent(String key) {
        return eventRepository.saveAndFlush(new DeadLetterEvent(
                "order-created-dlt",
                0,
                Math.abs(UUID.randomUUID().getLeastSignificantBits()),
                "order-created",
                0,
                19L,
                key,
                "{\"orderId\":42}",
                "{}",
                UUID.randomUUID(),
                "dlt-correlation-" + key,
                "java.lang.IllegalStateException",
                "test failure"
        ));
    }

    private String createToken(String role) {
        String email = role.toLowerCase() + "-dlt-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(new User(
                role + " DLT Operator",
                email,
                "Backend Operations",
                passwordEncoder.encode("password123"),
                role
        ));
        return jwtService.generateToken(user);
    }
}
