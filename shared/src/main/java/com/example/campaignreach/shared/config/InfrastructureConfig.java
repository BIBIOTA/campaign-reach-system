package com.example.campaignreach.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Base cross-module infrastructure configuration (design.md §3, §9, §10).
 *
 * <p>Lives in the shared kernel so {@code campaign} and {@code reach} share one
 * connectivity baseline without depending on each other. It only wires base
 * config; it defines no Kafka topic / partition key / consumer group (task 2.x),
 * no event schema (task 2.1) and no domain entity or migration (task 3+).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Enable Spring scheduling infrastructure ({@link EnableScheduling}). The
 *       concrete scheduled jobs (lifecycle batch, reaper, aggregation) are later
 *       tasks; only the infrastructure is enabled here. It is placed in the
 *       shared kernel because both modules run scheduled work and share this
 *       deployable.</li>
 *   <li>Register the validated secret-bearing {@link EmailProviderProperties}.</li>
 * </ul>
 *
 * <p>PostgreSQL DataSource and Kafka connectivity are configured through the
 * standard Spring Boot properties ({@code spring.datasource.*},
 * {@code spring.kafka.*}); their fail-fast validation lives in
 * {@link RequiredInfrastructurePropertiesValidator}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(EmailProviderProperties.class)
public class InfrastructureConfig {

    /**
     * Eagerly validates required connectivity properties (Kafka brokers, DB URL)
     * at context startup so a missing value fails fast with a clear error.
     */
    @Bean
    RequiredInfrastructurePropertiesValidator requiredInfrastructurePropertiesValidator(Environment environment) {
        RequiredInfrastructurePropertiesValidator validator =
                new RequiredInfrastructurePropertiesValidator(environment);
        validator.validate();
        return validator;
    }
}
