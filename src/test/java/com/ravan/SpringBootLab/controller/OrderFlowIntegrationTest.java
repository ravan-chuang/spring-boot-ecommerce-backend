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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class OrderFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventProducer eventProducer;

    @Test
    void shouldCreateOrderFromCartAndPaySuccessfully() throws Exception {
        Integer userId = createUser();
        Integer productId = createProduct();

        addProductToCart(userId, productId);

        Integer orderId = createOrder(userId);

        payOrder(orderId);
    }

    private Integer createUser() throws Exception {
        String userJson = """
                {
                  "name": "Order Flow Test User",
                  "email": "order-flow-test-user@example.com",
                  "skill": "Java Backend"
                }
                """;

        String response = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return extractId(response);
    }

    private Integer createProduct() throws Exception {
        String productJson = """
                {
                  "name": "Order Flow Test Product",
                  "description": "Product for full order flow integration test",
                  "price": 1200,
                  "stock": 10
                }
                """;

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return extractId(response);
    }

    private void addProductToCart(Integer userId, Integer productId) throws Exception {
        String cartJson = """
                {
                  "productId": %d,
                  "quantity": 2
                }
                """.formatted(productId);

        mockMvc.perform(post("/api/users/{userId}/cart/items", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartJson))
                .andExpect(status().isOk());
    }

    private Integer createOrder(Integer userId) throws Exception {
        String response = mockMvc.perform(post("/api/users/{userId}/orders", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return extractId(response);
    }

    private void payOrder(Integer orderId) throws Exception {
        String paymentJson = """
                {
                  "method": "CREDIT_CARD"
                }
                """;

        mockMvc.perform(post("/api/orders/{orderId}/payments", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "order-flow-test-" + orderId)
                        .content(paymentJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.method").value("CREDIT_CARD"));
    }

    private Integer extractId(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");

        if (data == null || data.isNull()) {
            throw new IllegalStateException("Response does not contain data field: " + responseBody);
        }

        if (data.has("id")) {
            return data.get("id").asInt();
        }

        if (data.has("userId")) {
            return data.get("userId").asInt();
        }

        if (data.has("productId")) {
            return data.get("productId").asInt();
        }

        if (data.has("orderId")) {
            return data.get("orderId").asInt();
        }

        throw new IllegalStateException("Could not extract id from response: " + responseBody);
    }
}
