package com.example.campaignreach.campaign.consumer;

import com.example.campaignreach.shared.event.KafkaTopics;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Poison-pill / dead-letter policy for the {@code domain.events} campaign-trigger consumer (task 6.3,
 * at-least-once §9). The shared {@code atLeastOnceKafkaListenerContainerFactory} picks up the single
 * {@link CommonErrorHandler} this contributes; with it, a behavior event that keeps failing is, after a
 * bounded number of in-memory retries, published to {@link KafkaTopics#DOMAIN_EVENTS_DLT} and its offset
 * advanced — so one bad message never blocks its partition indefinitely.
 *
 * <p><strong>Why a dedicated DLT and not {@code reach.dlq}.</strong> {@code reach.dlq} is the reach
 * dispatcher's <em>task</em> dead-letter (reach-task partition key, user-level), per design.md §9.
 * Routing an unprocessable inbound {@link DomainBehaviorEvent} there would pollute it with a foreign
 * message type and the wrong key, so failures here go to their own {@code domain.events.DLT}.
 *
 * <p><strong>Retry vs. dead-letter interaction.</strong> {@link FixedBackOff} gives a small finite
 * retry budget. A <em>transient</em> broker outage makes the DLT publish fail too (same broker), so the
 * recoverer throws, the offset is not committed, and the record is simply re-delivered later —
 * at-least-once is preserved and nothing is lost. Only a <em>deterministic</em> failure
 * (malformed payload, persistent processing error) actually reaches the DLT, where it stops blocking the
 * partition and is available for manual inspection / replay.
 *
 * <p>Gated behind the same {@code campaignreach.kafka.at-least-once.enabled} flag as the consumer, so it
 * is only built when the consumer is active (it never runs in a no-Kafka unit-test context).
 */
@Configuration
@ConditionalOnProperty(name = "campaignreach.kafka.at-least-once.enabled", havingValue = "true")
public class DomainEventErrorHandlingConfig {

    /** Bounded in-memory retry budget before a record is dead-lettered: 3 retries (4 total attempts). */
    private static final long RETRY_INTERVAL_MS = 1_000L;

    private static final long MAX_RETRIES = 3L;

    /**
     * Producer for the dead-letter topic. Keys stay strings (the user-id partition key); values use a
     * by-type serializer so a normally-deserialized {@link DomainBehaviorEvent} is re-serialized as JSON
     * while a raw {@code byte[]} from an upstream deserialization failure is forwarded verbatim.
     *
     * @param properties Spring Boot's bound {@code spring.kafka.*} configuration
     * @param sslBundles optional SSL bundle registry; absent when SSL is not configured
     * @return the dead-letter producer factory
     */
    @Bean
    public ProducerFactory<Object, Object> domainEventDltProducerFactory(
            KafkaProperties properties, org.springframework.beans.factory.ObjectProvider<SslBundles> sslBundles) {
        Map<String, Object> config = properties.buildProducerProperties(sslBundles.getIfAvailable());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // byte[] first so a raw deserialization-failure payload matches before the Object/JSON fallback.
        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(Object.class, new JsonSerializer<>());
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(config);
        factory.setValueSerializer(new DelegatingByTypeSerializer(delegates, true));
        return factory;
    }

    /**
     * Recoverer that publishes an exhausted record to {@link KafkaTopics#DOMAIN_EVENTS_DLT}, preserving
     * the partition so per-user ordering survives replay.
     *
     * @param producerFactory the dead-letter producer factory
     * @return the dead-letter publishing recoverer
     */
    @Bean
    public DeadLetterPublishingRecoverer domainEventDeadLetterRecoverer(
            ProducerFactory<Object, Object> producerFactory) {
        KafkaTemplate<Object, Object> dltTemplate = new KafkaTemplate<>(producerFactory);
        return new DeadLetterPublishingRecoverer(
                dltTemplate,
                (failedRecord, ex) -> new TopicPartition(KafkaTopics.DOMAIN_EVENTS_DLT, failedRecord.partition()));
    }

    /**
     * The {@link CommonErrorHandler} the shared at-least-once factory attaches: a finite
     * {@link FixedBackOff} retry budget, then dead-letter via the recoverer.
     *
     * @param recoverer the dead-letter publishing recoverer
     * @return the configured error handler
     */
    @Bean
    public CommonErrorHandler domainEventErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    }
}
