package com.example.campaignreach.shared.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Cross-module Kafka consumer container policy enforcing <strong>at-least-once</strong> delivery
 * (design.md §9). Lives in the shared kernel so both campaign and reach consumers share one
 * consumption-semantics baseline without depending on each other.
 *
 * <p>Two settings together guarantee "commit offset only after processing has been persisted":
 *
 * <ul>
 *   <li>Broker auto-commit is disabled ({@code enable.auto.commit=false}) so offsets are never
 *       advanced by a background timer.
 *   <li>The listener container ack mode is {@link ContainerProperties.AckMode#MANUAL_IMMEDIATE}, so a
 *       listener commits an offset only by explicitly acknowledging after it has finished persisting.
 * </ul>
 *
 * <p>On re-delivery the consumer can safely resume; idempotency is provided by the consumers' DB
 * unique constraints (reach_request batch key, reach_task four-column key), not by broker
 * exactly-once. This class defines policy only — it wires no {@code @KafkaListener}, producer, or
 * topic-admin bean (those belong to tasks 6.x / 7.x / 9.x).
 */
@Configuration
public class KafkaConsumerConfig {

    /**
     * Consumer factory with broker auto-commit disabled. Concrete deserializers and the bootstrap
     * server list are taken from the standard {@code spring.kafka.consumer.*} properties. SSL bundles
     * are forwarded when configured so TLS connections apply the right trust material.
     *
     * @param kafkaProperties Spring Boot's bound {@code spring.kafka.*} configuration
     * @param sslBundles optional SSL bundle registry; absent when SSL is not configured
     * @return a consumer factory that never auto-commits offsets
     */
    @Bean
    public ConsumerFactory<Object, Object> atLeastOnceConsumerFactory(
            KafkaProperties kafkaProperties, ObjectProvider<SslBundles> sslBundles) {
        Map<String, Object> config =
                new HashMap<>(kafkaProperties.buildConsumerProperties(sslBundles.getIfAvailable()));
        // At-least-once: offsets are advanced only after the listener persists and acknowledges.
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Listener container factory whose ack mode is {@link ContainerProperties.AckMode#MANUAL_IMMEDIATE},
     * so offsets commit only on explicit post-persistence acknowledgement. The configurer applies all
     * {@code spring.kafka.listener.*} auto-configuration first; the ack-mode override follows to
     * guarantee MANUAL_IMMEDIATE regardless of what the property file sets.
     *
     * @param configurer Spring Boot configurer that applies {@code spring.kafka.listener.*} settings
     * @param consumerFactory the auto-commit-disabled consumer factory
     * @return a container factory enforcing manual, post-persistence offset commits
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
