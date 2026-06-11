package com.example.campaignreach.reach.channel;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Always-on wiring for the Email channel adapter (task 8.1, spec §6 NFR-004): registers {@link
 * EmailChannelProperties} and exposes the configured {@link CircuitBreaker} that {@link EmailAdapter}
 * wraps around the provider call.
 *
 * <p>The breaker is built from {@link EmailChannelProperties#toCircuitBreakerConfig()} so all NFR-004
 * tunables (sliding window, failure-rate threshold, minimum calls, cool-down, half-open probes) come
 * from {@code campaignreach.reach.email.circuit-breaker.*} with the documented defaults.
 */
@Configuration
@EnableConfigurationProperties(EmailChannelProperties.class)
public class EmailChannelConfig {

    /** The named breaker instance guarding the Email provider call. */
    public static final String EMAIL_BREAKER_NAME = "emailChannel";

    /**
     * @param properties the bound Email-channel tunables
     * @return the breaker that {@link EmailAdapter} executes the provider call through
     */
    @Bean
    public CircuitBreaker emailChannelBreaker(EmailChannelProperties properties) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        return registry.circuitBreaker(EMAIL_BREAKER_NAME, properties.toCircuitBreakerConfig());
    }
}
