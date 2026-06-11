package com.example.campaignreach.reach.orchestrator;

import com.example.campaignreach.shared.event.ReachRequested;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Dedicated Kafka consumer wiring for the reach orchestrator's {@code reach.requested} listener
 * (task 7.1, at-least-once §9). Reach owns its <em>own</em> consumer + container factory rather than
 * reusing the shared kernel's {@code atLeastOnceKafkaListenerContainerFactory} for two reasons:
 *
 * <ul>
 *   <li><strong>Distinct payload type.</strong> The shared factory's consumer factory is bound to the
 *       campaign-pinned JSON deserializer ({@code spring.json.value.default.type =
 *       DomainBehaviorEvent}); this listener must deserialize {@link ReachRequested} from
 *       {@code com.example.campaignreach.shared.event}, so it configures its own typed
 *       {@link JsonDeserializer}.
 *   <li><strong>Isolation from campaign's error policy.</strong> The shared factory attaches the single
 *       {@code CommonErrorHandler} bean (campaign's {@code domain.events.DLT} dead-letter recoverer) to
 *       <em>every</em> listener using it. Routing a poison {@code reach.requested} record to
 *       {@code domain.events.DLT} would be wrong; reach keeps the framework-default seek-retry here
 *       (its own {@code reach.dlq} task dead-lettering is section-9 scope, not 7.1).
 * </ul>
 *
 * <p>The two settings that guarantee at-least-once mirror the shared kernel's baseline: broker
 * auto-commit is disabled and the container ack mode is
 * {@link ContainerProperties.AckMode#MANUAL_IMMEDIATE}, so the offset advances only when
 * {@link ReachRequestedConsumer} explicitly acknowledges after the batch is landed.
 *
 * <p>Gated behind the same {@code campaignreach.kafka.at-least-once.enabled} flag as the shared policy,
 * so it is only built when Kafka consumption is active (never in a no-Kafka unit-test context).
 */
@Configuration
@ConditionalOnProperty(name = "campaignreach.kafka.at-least-once.enabled", havingValue = "true")
public class ReachOrchestratorKafkaConfig {

    /**
     * Consumer factory for {@code reach.requested}: string keys, {@link ReachRequested} JSON values
     * wrapped in an {@link ErrorHandlingDeserializer} so a malformed payload becomes a recoverable failed
     * record instead of looping forever, and broker auto-commit disabled for at-least-once.
     *
     * @param kafkaProperties Spring Boot's bound {@code spring.kafka.*} configuration
     * @param sslBundles optional SSL bundle registry; absent when SSL is not configured
     * @return a typed, auto-commit-disabled consumer factory
     */
    @Bean
    public ConsumerFactory<String, ReachRequested> reachRequestedConsumerFactory(
            KafkaProperties kafkaProperties, ObjectProvider<SslBundles> sslBundles) {
        Map<String, Object> config =
                new HashMap<>(kafkaProperties.buildConsumerProperties(sslBundles.getIfAvailable()));
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        JsonDeserializer<ReachRequested> valueDeserializer = new JsonDeserializer<>(ReachRequested.class);
        valueDeserializer.addTrustedPackages("com.example.campaignreach.shared.event");
        return new DefaultKafkaConsumerFactory<>(
                config, new StringDeserializer(), new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    /**
     * Listener container factory whose ack mode is {@link ContainerProperties.AckMode#MANUAL_IMMEDIATE},
     * so offsets commit only on explicit post-landing acknowledgement. Referenced by name from
     * {@link ReachRequestedConsumer}.
     *
     * <p><strong>Why not the {@code ConcurrentKafkaListenerContainerFactoryConfigurer}.</strong> The
     * shared kernel applies {@code spring.kafka.listener.*} auto-config via that configurer, but its
     * signature is raw {@code <Object, Object>} (it drives deserialization through property class-names,
     * not typed instances). This factory deliberately keeps the strongly-typed
     * {@code <String, ReachRequested>} deserializer instances for clarity and compile-time safety, so it
     * sets the consumer factory directly. The trade-off is that {@code spring.kafka.listener.*} tuning
     * (concurrency, poll timeout, idle interval) is not auto-applied here — none is set in this project;
     * if such tuning is ever needed it must be configured on this factory explicitly.
     *
     * @param consumerFactory the typed, auto-commit-disabled consumer factory
     * @return the reach-requested listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReachRequested> reachRequestedKafkaListenerContainerFactory(
            ConsumerFactory<String, ReachRequested> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ReachRequested> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
