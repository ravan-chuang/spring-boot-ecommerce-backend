package com.ravan.SpringBootLab.integration;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.ravan.SpringBootLab.model.OutboxEvent;
import com.ravan.SpringBootLab.model.OutboxEventStatus;
import com.ravan.SpringBootLab.repository.OutboxEventRepository;
import com.ravan.SpringBootLab.service.EventProducer;
import com.ravan.SpringBootLab.service.OutboxEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "outbox.publisher.enabled=true",
        "outbox.publisher.initial-delay-ms=60000",
        "outbox.publisher.fixed-delay-ms=60000",
        "outbox.publisher.max-attempts=3",
        "spring.kafka.listener.auto-startup=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxEventPublisherRetryIntegrationTest
        extends TestcontainersIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @MockitoBean
    private EventProducer eventProducer;

    @BeforeEach
    void clearOutboxEvents() {
        outboxEventRepository.deleteAllInBatch();
    }

    @Test
    void shouldKeepEventPendingAndIncreaseRetryCountWhenKafkaPublishFails() {
        String eventKey = "outbox-retry-test-" + UUID.randomUUID();
        String payload = "{\"event\":\"outbox-retry-test\"}";

        OutboxEvent savedEvent = savePendingOrderEvent(eventKey, payload);

        doThrow(new RuntimeException("Kafka broker unavailable"))
                .when(eventProducer)
                .send(
                        KafkaTopicConfig.ORDER_CREATED_TOPIC,
                        eventKey,
                        payload,
                        savedEvent.getId()
                );

        outboxEventPublisher.publishPendingEvents();

        OutboxEvent retriedEvent = outboxEventRepository
                .findById(savedEvent.getId())
                .orElseThrow();

        assertEquals(OutboxEventStatus.PENDING, retriedEvent.getStatus());
        assertEquals(1, retriedEvent.getRetryCount());
        assertEquals("Kafka broker unavailable", retriedEvent.getLastError());
        assertNotNull(retriedEvent.getCreatedAt());

        verify(eventProducer).send(
                KafkaTopicConfig.ORDER_CREATED_TOPIC,
                eventKey,
                payload,
                savedEvent.getId()
        );
    }

    @Test
    void shouldMarkEventFailedAfterMaximumPublishAttempts() {
        String eventKey = "outbox-max-retry-test-" + UUID.randomUUID();
        String payload = "{\"event\":\"outbox-max-retry-test\"}";

        OutboxEvent savedEvent = savePendingOrderEvent(eventKey, payload);

        doThrow(new RuntimeException("Kafka broker unavailable"))
                .when(eventProducer)
                .send(
                        KafkaTopicConfig.ORDER_CREATED_TOPIC,
                        eventKey,
                        payload,
                        savedEvent.getId()
                );

        outboxEventPublisher.publishPendingEvents();
        outboxEventPublisher.publishPendingEvents();
        outboxEventPublisher.publishPendingEvents();

        OutboxEvent failedEvent = outboxEventRepository
                .findById(savedEvent.getId())
                .orElseThrow();

        assertEquals(OutboxEventStatus.FAILED, failedEvent.getStatus());
        assertEquals(3, failedEvent.getRetryCount());
        assertEquals("Kafka broker unavailable", failedEvent.getLastError());

        verify(eventProducer, times(3)).send(
                KafkaTopicConfig.ORDER_CREATED_TOPIC,
                eventKey,
                payload,
                savedEvent.getId()
        );
    }

    private OutboxEvent savePendingOrderEvent(String eventKey, String payload) {
        return outboxEventRepository.saveAndFlush(
                new OutboxEvent(
                        "ORDER",
                        eventKey,
                        "ORDER_CREATED",
                        KafkaTopicConfig.ORDER_CREATED_TOPIC,
                        payload
                )
        );
    }
}
