package com.ravan.SpringBootLab.integration;

import com.ravan.SpringBootLab.TestcontainersIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true"
})
class KafkaRetryDltIntegrationTest extends TestcontainersIntegrationTest {

    private static final String ORDER_CREATED_TOPIC = "order-created";
    private static final String ORDER_CREATED_DLT_TOPIC = "order-created-dlt";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    void shouldSendEmptyOrderCreatedMessageToDltAfterRetries() throws Exception {
        String testKey = "dlt-test-" + UUID.randomUUID();

        try (
                KafkaConsumer<String, String> dltConsumer =
                        new KafkaConsumer<>(consumerProperties());

                KafkaProducer<String, String> producer =
                        new KafkaProducer<>(producerProperties())
        ) {
            dltConsumer.subscribe(List.of(ORDER_CREATED_DLT_TOPIC));

            // Join the consumer group before publishing the test message.
            dltConsumer.poll(Duration.ofSeconds(1));

            producer.send(
                    new ProducerRecord<>(ORDER_CREATED_TOPIC, testKey, "")
            ).get(10, TimeUnit.SECONDS);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);

            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record :
                        dltConsumer.poll(Duration.ofMillis(500))) {

                    if (testKey.equals(record.key()) && "".equals(record.value())) {
                        return;
                    }
                }
            }

            fail("Expected the failed message to be published to " + ORDER_CREATED_DLT_TOPIC);
        }
    }

    private Properties producerProperties() {
        Properties properties = new Properties();

        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );
        properties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );

        return properties;
    }

    private Properties consumerProperties() {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlt-verification-" + UUID.randomUUID()
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        return properties;
    }
}
