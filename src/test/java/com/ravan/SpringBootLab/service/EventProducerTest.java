package com.ravan.SpringBootLab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravan.SpringBootLab.observability.CorrelationIds;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private EventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new EventProducer(
                kafkaTemplate,
                objectMapper
        );
    }

    @Test
    void shouldSendWithoutOptionalHeaders() throws Exception {
        mockSuccessfulSend("orders", 0, 10L);

        producer.send(
                "orders",
                null,
                "order-1",
                "{\"id\":1}",
                null,
                null
        );

        ProducerRecord<String, String> record =
                capturedRecord();

        assertEquals("orders", record.topic());
        assertNull(record.partition());
        assertEquals("order-1", record.key());
        assertEquals("{\"id\":1}", record.value());

        assertNull(
                record.headers()
                        .lastHeader(
                                EventProducer.OUTBOX_EVENT_ID_HEADER
                        )
        );

        assertNull(
                record.headers()
                        .lastHeader(
                                CorrelationIds.KAFKA_HEADER
                        )
        );

        verify(kafkaTemplate).flush();
    }

    @Test
    void shouldAddOutboxHeaderWhenEventIdExists() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockSuccessfulSend("orders", 1, 11L);

        producer.send(
                "orders",
                1,
                "order-2",
                "{\"id\":2}",
                eventId,
                null
        );

        ProducerRecord<String, String> record =
                capturedRecord();

        assertEquals(1, record.partition());

        assertArrayEquals(
                eventId.toString()
                        .getBytes(StandardCharsets.UTF_8),
                record.headers()
                        .lastHeader(
                                EventProducer.OUTBOX_EVENT_ID_HEADER
                        )
                        .value()
        );

        assertNull(
                record.headers()
                        .lastHeader(
                                CorrelationIds.KAFKA_HEADER
                        )
        );
    }

    @Test
    void shouldAddCorrelationHeaderWhenNonBlank() throws Exception {
        mockSuccessfulSend("orders", 0, 12L);

        producer.send(
                "orders",
                null,
                "order-3",
                "{\"id\":3}",
                null,
                "corr-123"
        );

        ProducerRecord<String, String> record =
                capturedRecord();

        assertArrayEquals(
                "corr-123".getBytes(StandardCharsets.UTF_8),
                record.headers()
                        .lastHeader(
                                CorrelationIds.KAFKA_HEADER
                        )
                        .value()
        );
    }

    @Test
    void shouldNotAddCorrelationHeaderWhenBlank() throws Exception {
        mockSuccessfulSend("orders", 0, 13L);

        producer.send(
                "orders",
                null,
                "order-4",
                "{\"id\":4}",
                null,
                "   "
        );

        ProducerRecord<String, String> record =
                capturedRecord();

        assertNull(
                record.headers()
                        .lastHeader(
                                CorrelationIds.KAFKA_HEADER
                        )
        );
    }

    private void mockSuccessfulSend(
            String topic,
            int partition,
            long offset
    ) {

        RecordMetadata metadata =
                new RecordMetadata(
                        new TopicPartition(topic, partition),
                        offset,
                        0,
                        System.currentTimeMillis(),
                        0,
                        0
                );

        SendResult<String, String> result =
                new SendResult<>(null, metadata);

        when(kafkaTemplate.send(
                org.mockito.ArgumentMatchers
                        .<ProducerRecord<String, String>>any()
        )).thenReturn(
                CompletableFuture.completedFuture(result)
        );
    }

    private ProducerRecord<String, String> capturedRecord() {

        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);

        verify(kafkaTemplate).send(captor.capture());

        return captor.getValue();
    }
}
