package com.ravan.SpringBootLab.controller;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.IdempotencyRecordRepository;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import com.ravan.SpringBootLab.repository.PaymentRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EventProducer eventProducer;

    @Test
    void shouldPersistPendingPaymentPaidOutboxEventWhenPaymentSucceeds() throws Exception {
        TestUser testUser = createTestUser("USER");
        Integer productId = createProductAndReturnId();

        addProductToCart(testUser.userId(), testUser.token(), productId);

        Integer orderId = createOrderAndReturnId(
                testUser.userId(),
                testUser.token()
        );

        Integer paymentId = payOrderAndReturnPaymentId(
                orderId,
                testUser.token(),
                "payment-outbox-" + UUID.randomUUID()
        );

        List<OutboxEvent> events =
                outboxEventRepository.findByAggregateTypeAndAggregateIdAndEventType(
                        "PAYMENT",
                        String.valueOf(paymentId),
                        "PAYMENT_PAID"
                );

        assertEquals(1, events.size());

        OutboxEvent event = events.getFirst();

        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(KafkaTopicConfig.PAYMENT_PAID_TOPIC, event.getTopic());
        assertEquals("PAYMENT", event.getAggregateType());
        assertEquals(String.valueOf(paymentId), event.getAggregateId());
        assertEquals("PAYMENT_PAID", event.getEventType());
        assertNotNull(event.getCreatedAt());

        assertTrue(event.getPayload().contains("\"paymentId\":" + paymentId));
        assertEquals(
                paymentId.intValue(),
                objectMapper.readTree(event.getPayload())
                        .get("paymentId")
                        .asInt()
        );
        assertEquals(
                orderId.intValue(),
                objectMapper.readTree(event.getPayload())
                        .get("orderId")
                        .asInt()
        );
    }

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
    void shouldReturnOnePaymentForConcurrentRequestsWithSameIdempotencyKey()
            throws Exception {
        TestUser testUser = createTestUser("USER");
        Integer productId = createProductAndReturnId();

        addProductToCart(testUser.userId(), testUser.token(), productId);
        Integer orderId = createOrderAndReturnId(
                testUser.userId(),
                testUser.token()
        );

        int requestCount = 8;
        String idempotencyKey = "payment-concurrent-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<ConcurrentPaymentResult>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return performPaymentRequest(
                            orderId,
                            testUser.token(),
                            idempotencyKey
                    );
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<ConcurrentPaymentResult> results = new ArrayList<>();
            for (Future<ConcurrentPaymentResult> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }

            assertTrue(results.stream().allMatch(result -> result.status() == 200));

            Set<Integer> paymentIds = results.stream()
                    .map(ConcurrentPaymentResult::paymentId)
                    .collect(Collectors.toSet());

            assertEquals(1, paymentIds.size());
            assertEquals(
                    1,
                    paymentRepository.countByOrder_Id(orderId)
            );
            assertEquals(
                    1,
                    idempotencyRecordRepository
                            .countByIdempotencyKeyAndRequestPath(
                                    idempotencyKey,
                                    "/api/orders/" + orderId + "/payments"
                            )
            );

            Integer paymentId = paymentIds.iterator().next();
            assertEquals(
                    1,
                    outboxEventRepository
                            .findByAggregateTypeAndAggregateIdAndEventType(
                                    "PAYMENT",
                                    String.valueOf(paymentId),
                                    "PAYMENT_PAID"
                            )
                            .size()
            );
        } finally {
            executor.shutdownNow();
        }
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


    private ConcurrentPaymentResult performPaymentRequest(
            Integer orderId,
            String token,
            String idempotencyKey
    ) throws Exception {
        String requestJson = """
                {
                  "method": "CREDIT_CARD"
                }
                """;

        var response = mockMvc.perform(post("/api/orders/{orderId}/payments", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(requestJson))
                .andReturn()
                .getResponse();

        JsonNode jsonNode = objectMapper.readTree(response.getContentAsString());
        Integer paymentId = response.getStatus() == 200
                ? jsonNode.get("data").get("id").asInt()
                : null;
        return new ConcurrentPaymentResult(response.getStatus(), paymentId);
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

    private record ConcurrentPaymentResult(int status, Integer paymentId) {
    }
}
