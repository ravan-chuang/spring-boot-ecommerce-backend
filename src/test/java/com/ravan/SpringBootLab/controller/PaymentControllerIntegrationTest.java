package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.UserRepository;
import com.ravan.SpringBootLab.security.JwtService;
import com.ravan.SpringBootLab.service.EventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest extends TestcontainersIntegrationTest {

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

    @MockitoBean
    private EventProducer eventProducer;

    @Test
    void shouldReturnSamePaymentWhenUsingSameIdempotencyKey() throws Exception {
        TestUser testUser = createTestUser("USER");
        Integer productId = createProductAndReturnId();

        addProductToCart(testUser.userId(), testUser.token(), productId);

        Integer orderId = createOrderAndReturnId(testUser.userId(), testUser.token());

        String idempotencyKey = "payment-test-key-" + System.currentTimeMillis();

        Integer firstPaymentId = payOrderAndReturnPaymentId(orderId, testUser.token(), idempotencyKey);
        Integer secondPaymentId = payOrderAndReturnPaymentId(orderId, testUser.token(), idempotencyKey);

        assertEquals(firstPaymentId, secondPaymentId);
    }

    @Test
    void shouldRejectPaymentWithoutIdempotencyKey() throws Exception {
        TestUser testUser = createTestUser("USER");
        Integer productId = createProductAndReturnId();

        addProductToCart(testUser.userId(), testUser.token(), productId);

        Integer orderId = createOrderAndReturnId(testUser.userId(), testUser.token());

        String requestJson = """
                {
                  "method": "CREDIT_CARD"
                }
                """;

        mockMvc.perform(post("/api/orders/{orderId}/payments", orderId)
                        .header("Authorization", "Bearer " + testUser.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    private Integer createProductAndReturnId() throws Exception {
        String requestJson = """
                {
                  "name": "Payment Test Product",
                  "description": "Product for payment idempotency test",
                  "price": 1000.00,
                  "stock": 10
                }
                """;

        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("data").get("id").asInt();
    }

    private void addProductToCart(Integer userId, String token, Integer productId) throws Exception {
        String requestJson = """
                {
                  "productId": %d,
                  "quantity": 1
                }
                """.formatted(productId);

        mockMvc.perform(post("/api/users/{userId}/cart/items", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());
    }

    private Integer createOrderAndReturnId(Integer userId, String token) throws Exception {
        String response = mockMvc.perform(post("/api/users/{userId}/orders", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("data").get("id").asInt();
    }

    private Integer payOrderAndReturnPaymentId(
            Integer orderId,
            String token,
            String idempotencyKey
    ) throws Exception {
        String requestJson = """
                {
                  "method": "CREDIT_CARD"
                }
                """;

        String response = mockMvc.perform(post("/api/orders/{orderId}/payments", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PAID")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("data").get("id").asInt();
    }

    private String createAdminToken() {
        TestUser admin = createTestUser("ADMIN");
        return admin.token();
    }

    private TestUser createTestUser(String role) {
        User user = createUserWithRole(role);
        String token = jwtService.generateToken(user);
        return new TestUser(user.getId(), token);
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

    private record TestUser(Integer userId, String token) {
    }
}
