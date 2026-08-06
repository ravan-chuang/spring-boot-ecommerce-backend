package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.UserRepository;
import com.ravan.SpringBootLab.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.publisher.enabled=false"
})
@AutoConfigureMockMvc
class UserAuthorizationIntegrationTest extends TestcontainersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldRejectAnonymousAccessToLegacyUserCrud() throws Exception {
        User user = createUser("USER");

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/{id}", user.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/{id}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest("Anonymous", "Security")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/{id}", user.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowUserToReadAndUpdateOnlyTheirOwnProfile() throws Exception {
        User owner = createUser("USER");
        User otherUser = createUser("USER");
        String ownerToken = jwtService.generateToken(owner);

        mockMvc.perform(get("/api/users/{id}", owner.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(owner.getId()));

        mockMvc.perform(put("/api/users/{id}", owner.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest("Updated Owner", "Platform Security")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Owner"))
                .andExpect(jsonPath("$.data.skill").value("Platform Security"));

        mockMvc.perform(get("/api/users/{id}", otherUser.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/{id}", otherUser.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReserveUserCollectionAndLegacyCreationForAdmins() throws Exception {
        User normalUser = createUser("USER");
        User admin = createUser("ADMIN");
        String userToken = jwtService.generateToken(normalUser);
        String adminToken = jwtService.generateToken(admin);

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest("Legacy User", "Java")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest("Admin Created", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Admin Created"));
    }

    @Test
    void shouldAllowAdminToManageAnyUserProfile() throws Exception {
        User admin = createUser("ADMIN");
        User target = createUser("USER");
        String adminToken = jwtService.generateToken(admin);

        mockMvc.perform(get("/api/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private User createUser(String role) {
        String email = role.toLowerCase() + "-user-api-" + UUID.randomUUID() + "@example.com";
        return userRepository.save(new User(
                role + " User API Test",
                email,
                "Backend Engineering",
                passwordEncoder.encode("password123"),
                role
        ));
    }

    private String createRequest(String name, String skill) {
        return """
                {
                  "name": "%s",
                  "skill": "%s"
                }
                """.formatted(name, skill);
    }

    private String updateRequest(String name, String skill) {
        return createRequest(name, skill);
    }

    @Test
    void shouldAllowAnonymousLivenessProbe() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldAllowAnonymousReadinessProbe() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

}
