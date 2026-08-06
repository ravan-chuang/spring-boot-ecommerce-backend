package com.ravan.SpringBootLab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.model.DeadLetterAuditAction;
import com.ravan.SpringBootLab.model.DeadLetterEvent;
import com.ravan.SpringBootLab.observability.CorrelationIds;
import com.ravan.SpringBootLab.repository.DeadLetterEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterCaptureServiceTest {

    private DeadLetterEventRepository repository;
    private DeadLetterAuditService auditService;
    private DeadLetterMetrics metrics;
    private DeadLetterCaptureService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeadLetterEventRepository.class);
        auditService = mock(DeadLetterAuditService.class);
        metrics = mock(DeadLetterMetrics.class);
        service = new DeadLetterCaptureService(
                repository,
                auditService,
                metrics,
                new ObjectMapper()
        );
        when(repository.saveAndFlush(any(DeadLetterEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void capturesOriginalCoordinatesHeadersAndTracingMetadata() {
        UUID outboxId = UUID.randomUUID();
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("order-created-dlt", 2, 41L, "order-42", "payload");
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_TOPIC, bytes("order-created"));
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_PARTITION,
                ByteBuffer.allocate(Integer.BYTES).putInt(1).array());
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_OFFSET,
                ByteBuffer.allocate(Long.BYTES).putLong(39L).array());
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_FQCN,
                bytes("java.lang.IllegalStateException"));
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, bytes("broken payload"));
        record.headers().add(EventProducer.OUTBOX_EVENT_ID_HEADER, bytes(outboxId.toString()));
        record.headers().add(CorrelationIds.KAFKA_HEADER, bytes("correlation-42"));
        record.headers().add(new RecordHeader("large", new byte[4_100]));

        DeadLetterEvent event = service.capture(record);

        assertThat(event.getDltTopic()).isEqualTo("order-created-dlt");
        assertThat(event.getDltPartition()).isEqualTo(2);
        assertThat(event.getDltOffset()).isEqualTo(41L);
        assertThat(event.getOriginalTopic()).isEqualTo("order-created");
        assertThat(event.getOriginalPartition()).isEqualTo(1);
        assertThat(event.getOriginalOffset()).isEqualTo(39L);
        assertThat(event.getMessageKey()).isEqualTo("order-42");
        assertThat(event.getPayload()).isEqualTo("payload");
        assertThat(event.getOutboxEventId()).isEqualTo(outboxId);
        assertThat(event.getCorrelationId()).isEqualTo("correlation-42");
        assertThat(event.getExceptionClass())
                .isEqualTo("java.lang.IllegalStateException");
        assertThat(event.getExceptionMessage()).isEqualTo("broken payload");
        assertThat(event.getHeadersJson()).contains("base64:", ":truncated");
        verify(auditService).record(
                eq(event.getId()), eq(DeadLetterAuditAction.CAPTURED),
                eq(null), org.mockito.ArgumentMatchers.contains("partition 2 offset 41")
        );
        verify(metrics).recordCaptured();
    }

    @Test
    void toleratesMalformedOptionalHeadersAndInfersOriginalTopic() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("payment-paid-dlt", 0, 3L, "   ", null);
        record.headers().add(KafkaHeaders.ORIGINAL_PARTITION, bytes("not-a-number"));
        record.headers().add(KafkaHeaders.ORIGINAL_OFFSET, bytes("not-a-number"));
        record.headers().add(EventProducer.OUTBOX_EVENT_ID_HEADER, bytes("not-a-uuid"));
        record.headers().add(CorrelationIds.KAFKA_HEADER, bytes("   "));

        DeadLetterEvent event = service.capture(record);

        assertThat(event.getOriginalTopic()).isEqualTo("payment-paid");
        assertThat(event.getOriginalPartition()).isNull();
        assertThat(event.getOriginalOffset()).isNull();
        assertThat(event.getMessageKey()).isNull();
        assertThat(event.getOutboxEventId()).isNull();
        assertThat(event.getCorrelationId()).isNull();
        assertThat(event.getExceptionClass()).isNull();
    }

    @Test
    void returnsPreviouslyCapturedCoordinateWithoutDuplicateAudit() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("order-created-dlt", 0, 9L, "key", "payload");
        DeadLetterEvent existing = new DeadLetterEvent(
                "order-created-dlt", 0, 9L, "order-created", 0, 8L,
                "key", "payload", "{}", null, null, null, null
        );
        when(repository.findByDltTopicAndDltPartitionAndDltOffset(
                "order-created-dlt", 0, 9L)).thenReturn(Optional.of(existing));

        assertThat(service.capture(record)).isSameAs(existing);
        verify(repository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any(), any(), any(), any());
        verify(metrics, never()).recordCaptured();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
