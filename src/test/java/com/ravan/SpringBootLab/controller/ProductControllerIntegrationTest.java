package com.ravan.SpringBootLab.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldCreateProductSuccessfullyWithAdminToken() throws Exception {
        String adminToken = createAdminToken();

        String requestJson = """
                {
                  "name": "Test Product",
                  "description": "Product created by integration test",
                  "price": 1000.00,
                  "stock": 10
                }
                """;

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Test Product")))
                .andExpect(jsonPath("$.data.description", is("Product created by integration test")))
                .andExpect(jsonPath("$.data.price", is(1000.00)))
                .andExpect(jsonPath("$.data.stock", is(10)));
    }

    @Test
    void shouldRejectCreateProductWithoutToken() throws Exception {
        String requestJson = """
                {
                  "name": "Unauthorized Product",
                  "description": "Should not be created",
                  "price": 1000.00,
                  "stock": 10
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectCreateProductWithUserToken() throws Exception {
        String userToken = createUserToken();

        String requestJson = """
                {
                  "name": "Forbidden Product",
                  "description": "Should not be created by USER",
                  "price": 1000.00,
                  "stock": 10
                }
                """;

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldGetProductByIdSuccessfullyWithoutToken() throws Exception {
        Integer productId = createProductAndReturnId();

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(productId)))
                .andExpect(jsonPath("$.data.name", is("Test Product")));
    }

    @Test
    void shouldUpdateProductSuccessfullyWithAdminToken() throws Exception {
        String adminToken = createAdminToken();
        Integer productId = createProductAndReturnId();

        String requestJson = """
                {
                  "name": "Updated Product",
                  "description": "Updated by integration test",
                  "price": 2000.00,
                  "stock": 20
                }
                """;

        mockMvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Updated Product")))
                .andExpect(jsonPath("$.data.description", is("Updated by integration test")))
                .andExpect(jsonPath("$.data.price", is(2000.00)))
                .andExpect(jsonPath("$.data.stock", is(20)));
    }

    @Test
    void shouldRejectUpdateProductWithUserToken() throws Exception {
        String userToken = createUserToken();
        Integer productId = createProductAndReturnId();

        String requestJson = """
                {
                  "name": "User Updated Product",
                  "description": "Should not be updated by USER",
                  "price": 2000.00,
                  "stock": 20
                }
                """;

        mockMvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDeleteProductSuccessfullyWithAdminToken() throws Exception {
        String adminToken = createAdminToken();
        Integer productId = createProductAndReturnId();

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectDeleteProductWithUserToken() throws Exception {
        String userToken = createUserToken();
        Integer productId = createProductAndReturnId();

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    private Integer createProductAndReturnId() throws Exception {
        String adminToken = createAdminToken();

        String requestJson = """
                {
                  "name": "Test Product",
                  "description": "Product created by integration test",
                  "price": 1000.00,
                  "stock": 10
                }
                """;

        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("data").get("id").asInt();
    }

    private String createAdminToken() {
        User admin = createUserWithRole("ADMIN");
        return jwtService.generateToken(admin);
    }

    private String createUserToken() {
        User user = createUserWithRole("USER");
        return jwtService.generateToken(user);
    }

    private User createUserWithRole(String role) {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";

        User user = new User(
                role + " Test User",
                email,
                "Java Backend",
                passwordEncoder.encode("password123"),
                role
        );

        return userRepository.save(user);
    }
}
