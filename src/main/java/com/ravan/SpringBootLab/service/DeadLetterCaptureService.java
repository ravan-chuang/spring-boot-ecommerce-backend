package com.ravan.SpringBootLab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.model.DeadLetterAuditAction;
import com.ravan.SpringBootLab.model.DeadLetterEvent;
import com.ravan.SpringBootLab.observability.CorrelationIds;
import com.ravan.SpringBootLab.repository.DeadLetterEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeadLetterCaptureService {

    private static final int MAX_HEADER_VALUE_BYTES = 4_096;

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final DeadLetterAuditService deadLetterAuditService;
    private final DeadLetterMetrics deadLetterMetrics;
    private final ObjectMapper objectMapper;

    public DeadLetterCaptureService(
            DeadLetterEventRepository deadLetterEventRepository,
            DeadLetterAuditService deadLetterAuditService,
            DeadLetterMetrics deadLetterMetrics,
            ObjectMapper objectMapper
    ) {
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.deadLetterAuditService = deadLetterAuditService;
        this.deadLetterMetrics = deadLetterMetrics;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeadLetterEvent capture(ConsumerRecord<String, String> record) {
        DeadLetterEvent existing = deadLetterEventRepository
                .findByDltTopicAndDltPartitionAndDltOffset(
                        record.topic(),
                        record.partition(),
                        record.offset()
                )
                .orElse(null);

        if (existing != null) {
            return existing;
        }

        String originalTopic = firstNonBlank(
                stringHeader(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                stringHeader(record, KafkaHeaders.ORIGINAL_TOPIC),
                inferOriginalTopic(record.topic())
        );

        DeadLetterEvent event = new DeadLetterEvent(
                record.topic(),
                record.partition(),
                record.offset(),
                originalTopic,
                integerHeader(
                        record,
                        KafkaHeaders.DLT_ORIGINAL_PARTITION,
                        KafkaHeaders.ORIGINAL_PARTITION
                ),
                longHeader(
                        record,
                        KafkaHeaders.DLT_ORIGINAL_OFFSET,
                        KafkaHeaders.ORIGINAL_OFFSET
                ),
                normalize(record.key(), 512),
                record.value(),
                serializeHeaders(record),
                uuidHeader(record, EventProducer.OUTBOX_EVENT_ID_HEADER),
                normalize(
                        stringHeader(record, CorrelationIds.KAFKA_HEADER),
                        128
                ),
                normalize(
                        firstNonBlank(
                                stringHeader(record, KafkaHeaders.DLT_EXCEPTION_FQCN),
                                stringHeader(record, KafkaHeaders.EXCEPTION_FQCN)
                        ),
                        255
                ),
                firstNonBlank(
                        stringHeader(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                        stringHeader(record, KafkaHeaders.EXCEPTION_MESSAGE)
                )
        );

        DeadLetterEvent savedEvent = deadLetterEventRepository.saveAndFlush(event);

        deadLetterAuditService.record(
                savedEvent.getId(),
                DeadLetterAuditAction.CAPTURED,
                null,
                "Captured terminal message from "
                        + record.topic()
                        + " partition "
                        + record.partition()
                        + " offset "
                        + record.offset()
        );
        deadLetterMetrics.recordCaptured();

        return savedEvent;
    }

    private String serializeHeaders(ConsumerRecord<String, String> record) {
        Map<String, List<String>> headers = new LinkedHashMap<>();

        for (Header header : record.headers()) {
            byte[] value = header.value();
            int length = value == null ? 0 : Math.min(
                    value.length,
                    MAX_HEADER_VALUE_BYTES
            );
            byte[] boundedValue = value == null
                    ? new byte[0]
                    : java.util.Arrays.copyOf(value, length);
            String encoded = "base64:"
                    + Base64.getEncoder().encodeToString(boundedValue);

            if (value != null && value.length > MAX_HEADER_VALUE_BYTES) {
                encoded += ":truncated";
            }

            headers.computeIfAbsent(header.key(), ignored -> new ArrayList<>())
                    .add(encoded);
        }

        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize dead-letter headers",
                    exception
            );
        }
    }

    private String stringHeader(
            ConsumerRecord<String, String> record,
            String name
    ) {
        Header header = record.headers().lastHeader(name);

        if (header == null || header.value() == null) {
            return null;
        }

        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private UUID uuidHeader(
            ConsumerRecord<String, String> record,
            String name
    ) {
        String value = stringHeader(record, name);

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Integer integerHeader(
            ConsumerRecord<String, String> record,
            String... names
    ) {
        Header header = lastHeader(record, names);

        if (header == null || header.value() == null) {
            return null;
        }

        if (header.value().length == Integer.BYTES) {
            return ByteBuffer.wrap(header.value()).getInt();
        }

        try {
            return Integer.valueOf(
                    new String(header.value(), StandardCharsets.UTF_8)
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long longHeader(
            ConsumerRecord<String, String> record,
            String... names
    ) {
        Header header = lastHeader(record, names);

        if (header == null || header.value() == null) {
            return null;
        }

        if (header.value().length == Long.BYTES) {
            return ByteBuffer.wrap(header.value()).getLong();
        }

        try {
            return Long.valueOf(
                    new String(header.value(), StandardCharsets.UTF_8)
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Header lastHeader(
            ConsumerRecord<String, String> record,
            String... names
    ) {
        for (String name : names) {
            Header header = record.headers().lastHeader(name);

            if (header != null) {
                return header;
            }
        }

        return null;
    }

    private String inferOriginalTopic(String dltTopic) {
        if (dltTopic != null && dltTopic.endsWith("-dlt")) {
            return dltTopic.substring(0, dltTopic.length() - 4);
        }

        return dltTopic;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), maxLength));
    }
}
