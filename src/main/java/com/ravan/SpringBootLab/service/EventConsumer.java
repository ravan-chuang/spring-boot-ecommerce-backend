package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.ravan.SpringBootLab.model.DeadLetterEvent;
import com.ravan.SpringBootLab.observability.CorrelationIds;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class EventConsumer {

    private static final Logger logger =
            LoggerFactory.getLogger(EventConsumer.class);

    private static final String ORDER_CREATED_CONSUMER =
            "order-created-consumer";

    private static final String PAYMENT_PAID_CONSUMER =
            "payment-paid-consumer";

    private final ProcessedEventService processedEventService;
    private final OrderEventAuditService orderEventAuditService;
    private final DeadLetterCaptureService deadLetterCaptureService;

    public EventConsumer(
            ProcessedEventService processedEventService,
            OrderEventAuditService orderEventAuditService,
            DeadLetterCaptureService deadLetterCaptureService
    ) {
        this.processedEventService = processedEventService;
        this.orderEventAuditService = orderEventAuditService;
        this.deadLetterCaptureService = deadLetterCaptureService;
    }

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(
                    delay = 1000,
                    multiplier = 2.0,
                    maxDelay = 4000
            ),
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = KafkaTopicConfig.ORDER_CREATED_TOPIC,
            groupId = ORDER_CREATED_CONSUMER
    )
    public void handleOrderCreatedEvent(
            ConsumerRecord<String, String> record
    ) {
        processWithIdempotency(
                record,
                ORDER_CREATED_CONSUMER,
                eventId -> processOrderCreatedEvent(
                        eventId,
                        record.value()
                )
        );
    }

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(
                    delay = 1000,
                    multiplier = 2.0,
                    maxDelay = 4000
            ),
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = KafkaTopicConfig.PAYMENT_PAID_TOPIC,
            groupId = PAYMENT_PAID_CONSUMER
    )
    public void handlePaymentPaidEvent(
            ConsumerRecord<String, String> record
    ) {
        processWithIdempotency(
                record,
                PAYMENT_PAID_CONSUMER,
                eventId -> processPaymentPaidEvent(eventId, record.value())
        );
    }

    @DltHandler
    public void handleDeadLetterMessage(
            ConsumerRecord<String, String> record
    ) {
        withRecordContext(record, () -> {
            DeadLetterEvent event = deadLetterCaptureService.capture(record);

            logger.error(
                    "Message persisted for DLT operations: deadLetterId={}, topic={}, partition={}, offset={}, key={}",
                    event.getId(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key()
            );
        });
    }

    private void processWithIdempotency(
            ConsumerRecord<String, String> record,
            String consumerName,
            Consumer<UUID> businessAction
    ) {
        withRecordContext(record, () -> {
            UUID eventId = extractOutboxEventId(record);

            if (eventId == null) {
                logger.warn(
                        "Received event without outbox-event-id header; processing without deduplication: topic={}, offset={}",
                        record.topic(),
                        record.offset()
                );

                businessAction.accept(null);
                return;
            }

            processedEventService.processIfFirstTime(
                    eventId,
                    consumerName,
                    () -> businessAction.accept(eventId)
            );
        });
    }

    private UUID extractOutboxEventId(
            ConsumerRecord<String, String> record
    ) {
        Header header = record.headers()
                .lastHeader(EventProducer.OUTBOX_EVENT_ID_HEADER);

        if (header == null || header.value() == null) {
            return null;
        }

        try {
            return UUID.fromString(
                    new String(header.value(), StandardCharsets.UTF_8)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid outbox-event-id header: "
                            + new String(
                                    header.value(),
                                    StandardCharsets.UTF_8
                            ),
                    exception
            );
        }
    }

    private void processOrderCreatedEvent(
            UUID eventId,
            String message
    ) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "OrderCreatedEvent message is empty"
            );
        }

        if (eventId != null) {
            orderEventAuditService.recordOrderCreatedEvent(
                    eventId,
                    message
            );
        }

        logger.info(
                "Consumed OrderCreatedEvent: eventId={}, payloadBytes={}",
                eventId,
                message.getBytes(StandardCharsets.UTF_8).length
        );
    }

    private void processPaymentPaidEvent(UUID eventId, String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "PaymentPaidEvent message is empty"
            );
        }

        logger.info(
                "Consumed PaymentPaidEvent: eventId={}, payloadBytes={}",
                eventId,
                message.getBytes(StandardCharsets.UTF_8).length
        );
    }

    private void withRecordContext(
            ConsumerRecord<String, String> record,
            Runnable action
    ) {
        String previousCorrelationId = MDC.get(CorrelationIds.MDC_KEY);
        Header correlationHeader = record.headers()
                .lastHeader(CorrelationIds.KAFKA_HEADER);
        String candidate = correlationHeader == null
                || correlationHeader.value() == null
                ? null
                : new String(
                        correlationHeader.value(),
                        StandardCharsets.UTF_8
                );
        String correlationId = CorrelationIds.normalizeOrGenerate(candidate);

        MDC.put(CorrelationIds.MDC_KEY, correlationId);

        try {
            action.run();
        } finally {
            if (previousCorrelationId == null) {
                MDC.remove(CorrelationIds.MDC_KEY);
            } else {
                MDC.put(CorrelationIds.MDC_KEY, previousCorrelationId);
            }
        }
    }
}
