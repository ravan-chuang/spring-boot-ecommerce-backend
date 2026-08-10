package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.observability.CorrelationIds;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private ProcessedEventService processedEventService;

    @Mock
    private OrderEventAuditService orderEventAuditService;

    @Mock
    private DeadLetterCaptureService deadLetterCaptureService;

    private EventConsumer consumer;

    @BeforeEach
    void setUp() {
        MDC.clear();

        consumer = new EventConsumer(
                processedEventService,
                orderEventAuditService,
                deadLetterCaptureService
        );
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void paymentPaidShouldProcessValidEventWithOutboxId() {
        UUID eventId = UUID.randomUUID();

        ConsumerRecord<String, String> record =
                record("payment-paid", "payload");

        record.headers().add(
                EventProducer.OUTBOX_EVENT_ID_HEADER,
                bytes(eventId.toString())
        );

        executeFirstTimeAction();

        consumer.handlePaymentPaidEvent(record);

        verify(processedEventService)
                .processIfFirstTime(
                        eq(eventId),
                        eq("payment-paid-consumer"),
                        any(Runnable.class)
                );

        verifyNoInteractions(orderEventAuditService);
    }

    @Test
    void paymentPaidShouldProcessWithoutDeduplicationWhenHeaderMissing() {
        ConsumerRecord<String, String> record =
                record("payment-paid", "payload");

        consumer.handlePaymentPaidEvent(record);

        verifyNoInteractions(processedEventService);
        verifyNoInteractions(orderEventAuditService);
    }

    @Test
    void paymentPaidShouldProcessWithoutDeduplicationWhenHeaderValueIsNull() {
        ConsumerRecord<String, String> record =
                record("payment-paid", "payload");

        record.headers().add(
                new RecordHeader(
                        EventProducer.OUTBOX_EVENT_ID_HEADER,
                        null
                )
        );

        consumer.handlePaymentPaidEvent(record);

        verifyNoInteractions(processedEventService);
    }

    @Test
    void paymentPaidShouldRejectInvalidOutboxEventId() {
        ConsumerRecord<String, String> record =
                record("payment-paid", "payload");

        record.headers().add(
                EventProducer.OUTBOX_EVENT_ID_HEADER,
                bytes("not-a-uuid")
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> consumer.handlePaymentPaidEvent(record)
                );

        assertEquals(
                "Invalid outbox-event-id header: not-a-uuid",
                exception.getMessage()
        );

        verifyNoInteractions(processedEventService);
    }

    @Test
    void paymentPaidShouldRejectNullPayload() {
        ConsumerRecord<String, String> record =
                record("payment-paid", null);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> consumer.handlePaymentPaidEvent(record)
                );

        assertEquals(
                "PaymentPaidEvent message is empty",
                exception.getMessage()
        );
    }

    @Test
    void paymentPaidShouldRejectBlankPayload() {
        ConsumerRecord<String, String> record =
                record("payment-paid", "   ");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> consumer.handlePaymentPaidEvent(record)
                );

        assertEquals(
                "PaymentPaidEvent message is empty",
                exception.getMessage()
        );
    }

    @Test
    void orderCreatedWithoutOutboxIdShouldSkipAudit() {
        ConsumerRecord<String, String> record =
                record("order-created", "{\"orderId\":1}");

        consumer.handleOrderCreatedEvent(record);

        verifyNoInteractions(processedEventService);

        verify(
                orderEventAuditService,
                never()
        ).recordOrderCreatedEvent(
                any(UUID.class),
                any(String.class)
        );
    }

    @Test
    void orderCreatedWithOutboxIdShouldRecordAudit() {
        UUID eventId = UUID.randomUUID();

        ConsumerRecord<String, String> record =
                record(
                        "order-created",
                        "{\"orderId\":1}"
                );

        record.headers().add(
                EventProducer.OUTBOX_EVENT_ID_HEADER,
                bytes(eventId.toString())
        );

        executeFirstTimeAction();

        consumer.handleOrderCreatedEvent(record);

        verify(orderEventAuditService)
                .recordOrderCreatedEvent(
                        eventId,
                        "{\"orderId\":1}"
                );
    }

    @Test
    void shouldRestorePreviousCorrelationIdAfterProcessing() {
        MDC.put(
                CorrelationIds.MDC_KEY,
                "previous-correlation-id"
        );

        ConsumerRecord<String, String> record =
                record("payment-paid", "payload");

        record.headers().add(
                CorrelationIds.KAFKA_HEADER,
                bytes("incoming-correlation-id")
        );

        consumer.handlePaymentPaidEvent(record);

        assertEquals(
                "previous-correlation-id",
                MDC.get(CorrelationIds.MDC_KEY)
        );
    }

    @Test
    void shouldRemoveGeneratedCorrelationIdWhenNoPreviousContextExists() {
        ConsumerRecord<String, String> record =
                record("payment-paid", "payload");

        consumer.handlePaymentPaidEvent(record);

        assertNull(
                MDC.get(CorrelationIds.MDC_KEY)
        );
    }

    @Test
    void shouldHandleCorrelationHeaderWithNullValue() {
        ConsumerRecord<String, String> record =
                record("payment-paid", "payload");

        record.headers().add(
                new RecordHeader(
                        CorrelationIds.KAFKA_HEADER,
                        null
                )
        );

        consumer.handlePaymentPaidEvent(record);

        assertNull(
                MDC.get(CorrelationIds.MDC_KEY)
        );
    }

    private void executeFirstTimeAction() {
        doAnswer(invocation -> {
            Runnable action =
                    invocation.getArgument(2);

            action.run();

            return true;
        }).when(processedEventService)
                .processIfFirstTime(
                        any(UUID.class),
                        any(String.class),
                        any(Runnable.class)
                );
    }

    private ConsumerRecord<String, String> record(
            String topic,
            String payload
    ) {
        return new ConsumerRecord<>(
                topic,
                0,
                1L,
                "key",
                payload
        );
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
