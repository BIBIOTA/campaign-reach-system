package com.example.campaignreach.reach.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campaignreach.shared.event.ReachRequested;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Regression test for the reach consumer factory's deserializer wiring.
 *
 * <p>{@code application.yml} pins the <em>campaign</em> consumer's deserializer via PROPERTIES
 * ({@code spring.deserializer.* / spring.json.*}, default type {@code DomainBehaviorEvent}). The reach
 * factory instead passes typed deserializer INSTANCES, so it must strip those property-based keys from
 * its copy of the consumer config — otherwise a {@link JsonDeserializer} configured both by setters and
 * by {@code spring.json.*} properties throws "must be configured with property setters, or via
 * configuration properties; not both" when the listener container starts, tearing down the whole
 * {@code @SpringBootTest} context (and failing every integration test).
 */
class ReachOrchestratorKafkaConfigTest {

    @Test
    @DisplayName("reach consumer factory strips campaign's property-based deserializer config (instances win)")
    void stripsPropertyBasedDeserializerConfig() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(java.util.List.of("localhost:9092"));
        // Mirror application.yml: the campaign consumer's property-pinned JSON deserializer.
        kafkaProperties.getConsumer().setKeyDeserializer(StringDeserializer.class);
        kafkaProperties.getConsumer().setValueDeserializer(ErrorHandlingDeserializer.class);
        kafkaProperties
                .getConsumer()
                .getProperties()
                .put("spring.deserializer.value.delegate.class", JsonDeserializer.class.getName());
        kafkaProperties
                .getConsumer()
                .getProperties()
                .put(
                        "spring.json.value.default.type",
                        "com.example.campaignreach.campaign.consumer.DomainBehaviorEvent");
        kafkaProperties
                .getConsumer()
                .getProperties()
                .put("spring.json.trusted.packages", "com.example.campaignreach.campaign.consumer");

        ObjectProvider<SslBundles> noSslBundles = noopObjectProvider();
        DefaultKafkaConsumerFactory<String, ReachRequested> factory =
                (DefaultKafkaConsumerFactory<String, ReachRequested>)
                        new ReachOrchestratorKafkaConfig().reachRequestedConsumerFactory(kafkaProperties, noSslBundles);

        Map<String, Object> config = factory.getConfigurationProperties();
        assertThat(config)
                .doesNotContainKeys(
                        "spring.json.value.default.type",
                        "spring.json.trusted.packages",
                        "spring.deserializer.value.delegate.class",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
        // At-least-once: broker auto-commit stays disabled.
        assertThat(config).containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    }

    /** Minimal {@link ObjectProvider} whose {@code getIfAvailable()} returns {@code null} (no SSL bundles). */
    private static ObjectProvider<SslBundles> noopObjectProvider() {
        return new ObjectProvider<>() {
            @Override
            public SslBundles getObject(Object... args) {
                return null;
            }

            @Override
            public SslBundles getObject() {
                return null;
            }

            @Override
            public SslBundles getIfAvailable() {
                return null;
            }

            @Override
            public SslBundles getIfUnique() {
                return null;
            }
        };
    }
}
