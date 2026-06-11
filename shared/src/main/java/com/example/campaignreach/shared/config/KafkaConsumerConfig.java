package com.example.campaignreach.shared.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
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
 * exactly-once. This class wires no {@code @KafkaListener}, producer, or topic-admin bean (those
 * belong to tasks 6.x / 7.x / 9.x) — it only contributes the consumer/container <em>policy</em> beans.
 *
 * <p><strong>This is active Spring configuration, not a passive contract.</strong> Because the
 * shared kernel is under the application's component-scan root, this class would otherwise build its
 * beans in every context. It is therefore gated behind {@code campaignreach.kafka.at-least-once.enabled}
 * (default off): until a consumer module (6.x) opts in, no beans are created and Spring Boot's Kafka
 * auto-configuration is left untouched. The container-factory bean is deliberately named
 * {@code atLeastOnceKafkaListenerContainerFactory} (not the auto-config default
 * {@code kafkaListenerContainerFactory}) so it never silently overrides the default; consumers
 * reference it explicitly via {@code @KafkaListener(containerFactory = "atLeastOnceKafkaListenerContainerFactory")}.
 */
@Configuration
@ConditionalOnProperty(name = "campaignreach.kafka.at-least-once.enabled", havingValue = "true")
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
     * <p>Named {@code atLeastOnceKafkaListenerContainerFactory} rather than the auto-config default
     * {@code kafkaListenerContainerFactory} so it never silently shadows the default bean; consumers
     * must opt in explicitly via {@code @KafkaListener(containerFactory = "atLeastOnceKafkaListenerContainerFactory")}.
     *
     * <p><strong>Poison-pill handling.</strong> A consumer module may contribute a single
     * {@link CommonErrorHandler} bean (e.g. a {@code DefaultErrorHandler} with finite retries + a
     * dead-letter recoverer); when present it is attached here so a record that keeps failing is, after
     * its retries, dead-lettered and its offset advanced instead of blocking the partition forever. When
     * absent the container keeps the framework default (infinite seek-retry), preserving at-least-once.
     * Exactly one such bean may exist per application context; with several at-least-once consumers the
     * policy must be unified rather than letting one module's handler bind to all listeners.
     *
     * @param configurer Spring Boot configurer that applies {@code spring.kafka.listener.*} settings
     * @param consumerFactory the auto-commit-disabled consumer factory
     * @param errorHandler optional consumer-supplied poison-pill / dead-letter policy
     * @return a container factory enforcing manual, post-persistence offset commits
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> atLeastOnceKafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            ObjectProvider<CommonErrorHandler> errorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        errorHandler.ifUnique(factory::setCommonErrorHandler);
        return factory;
    }
}
