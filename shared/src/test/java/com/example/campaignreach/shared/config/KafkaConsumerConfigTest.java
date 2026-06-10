package com.example.campaignreach.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Verifies the at-least-once consumer container policy (design.md §9, task 2.2): offsets must commit
 * only after the listener persists. Asserting the two settings without a live broker keeps the gate
 * fast while still pinning the contract that re-delivery can safely resume.
 */
class KafkaConsumerConfigTest {

    private final KafkaConsumerConfig config = new KafkaConsumerConfig();

    @SuppressWarnings("unchecked")
    private ObjectProvider<SslBundles> emptySslBundles() {
        return mock(ObjectProvider.class);
    }

    @Test
    void consumerFactoryDisablesBrokerAutoCommit() {
        ConsumerFactory<Object, Object> factory =
                config.atLeastOnceConsumerFactory(new KafkaProperties(), emptySslBundles());

        Object autoCommit = factory.getConfigurationProperties().get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG);

        assertThat(autoCommit).isEqualTo(false);
    }

    @Test
    void listenerContainerAckModeIsManualImmediate() {
        ConsumerFactory<Object, Object> consumerFactory =
                config.atLeastOnceConsumerFactory(new KafkaProperties(), emptySslBundles());
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer =
                mock(ConcurrentKafkaListenerContainerFactoryConfigurer.class);

        ConcurrentKafkaListenerContainerFactory<Object, Object> listenerFactory =
                config.atLeastOnceKafkaListenerContainerFactory(configurer, consumerFactory);

        assertThat(listenerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    }
}
