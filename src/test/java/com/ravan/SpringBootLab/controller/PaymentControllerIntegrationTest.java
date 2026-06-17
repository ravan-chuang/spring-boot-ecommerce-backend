package com.ravan.SpringBootLab.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.service.EventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventProducer eventProducer;

    @Test
    void shouldReturnSamePaymentWhenUsingSameIdempotencyKey() throws Exception {
        Integer userId = createUserAndReturnId();
        Integer productId = createProductAndReturnId();

        addProductToCart(userId, productId);

        Integer orderId = createOrderAndReturnId(userId);

        String idempotencyKey = "payment-test-key-" + System.currentTimeMillis();

        Integer firstPaymentId = payOrderAndReturnPaymentId(orderId, idempotencyKey);
        Integer secondPaymentId = payOrderAndReturnPaymentId(orderId, idempotencyKey);

        assertEquals(firstPaymentId, secondPaymentId);
    }

    @Test
    void shouldRejectPaymentWithoutIdempotencyKey() throws Exception {
        Integer userId = createUserAndReturnId();
        Integer productId = createProductAndReturnId();

        addProductToCart(userId, productId);

        Integer orderId = createOrderAndReturnId(userId);

        String requestJson = """
                {
                  "method": "CREDIT_CARD"
                }
                """;

        mockMvc.perform(post("/api/orders/{orderId}/payments", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    private Integer createUserAndReturnId() throws Exception {
        String requestJson = """
                {
                  "name": "Payment Test User",
                  "email": "payment-test-user-%d@example.com",
                  "password": "password123",
                  "skill": "Java Backend"
                }
                """.formatted(System.currentTimeMillis());

        String response = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("data").get("id").asInt();
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("data").get("id").asInt();
    }

    private void addProductToCart(Integer userId, Integer productId) throws Exception {
        String requestJson = """
                {
                  "productId": %d,
                  "quantity": 1
                }
                """.formatted(productId);

        mockMvc.perform(post("/api/users/{userId}/cart/items", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());
    }

    private Integer createOrderAndReturnId(Integer userId) throws Exception {
        String response = mockMvc.perform(post("/api/users/{userId}/orders", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("data").get("id").asInt();
    }

    private Integer payOrderAndReturnPaymentId(Integer orderId, String idempotencyKey) throws Exception {
        String requestJson = """
                {
                  "method": "CREDIT_CARD"
                }
                """;

        String response = mockMvc.perform(post("/api/orders/{orderId}/payments", orderId)
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
}
