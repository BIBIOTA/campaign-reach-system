package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Smoke-level integration test proving the Testcontainers base infrastructure
 * works (design.md §7). It does NOT exercise any business behaviour (events,
 * audience expansion, ReachTask persistence) — those are later tasks (2/6/7+).
 *
 * <p>Acceptance (tasks.md 1.3): WHEN integration tests run THEN the context boots
 * on a real Kafka broker and a real PostgreSQL database, with the broker never
 * mocked.
 */
class InfrastructureSmokeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaProperties kafkaProperties;

    /**
     * The full Spring context starts using the container-supplied connectivity
     * properties — including the fail-fast {@code RequiredInfrastructurePropertiesValidator}
     * bean, which only exists if the real Kafka and PostgreSQL properties were
     * injected by {@code @ServiceConnection}.
     */
    @Test
    void integrationContextStartsWithRealKafkaAndPostgres() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBean("requiredInfrastructurePropertiesValidator"))
                .isNotNull();

        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(KAFKA.isRunning()).isTrue();

        // The injected broker address is the real container, not a mock.
        assertThat(kafkaProperties.getBootstrapServers()).containsExactly(KAFKA.getBootstrapServers());
    }

    /**
     * Round-trips a record through the real broker to prove it is a live Kafka
     * server (the broker is NOT mocked, design.md §7).
     */
    @Test
    void roundTripsAMessageThroughTheRealBroker() {
        String topic = "infra-smoke-topic";

        kafkaTemplate.send(topic, "smoke-key", "smoke-value");
        kafkaTemplate.flush();

        try (Consumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(java.util.List.of(topic));
            ConsumerRecord<String, String> received = pollSingle(consumer);

            assertThat(received).isNotNull();
            assertThat(received.key()).isEqualTo("smoke-key");
            assertThat(received.value()).isEqualTo("smoke-value");
        }
    }

    private Consumer<String, String> newConsumer() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put("group.id", "infra-smoke-consumer");
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", StringDeserializer.class);
        props.remove(JsonDeserializer.TRUSTED_PACKAGES);
        ConsumerFactory<String, String> factory =
                new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer());
        return factory.createConsumer();
    }

    private ConsumerRecord<String, String> pollSingle(Consumer<String, String> consumer) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        return null;
    }
}
