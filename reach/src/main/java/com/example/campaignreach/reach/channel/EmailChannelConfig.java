package com.example.campaignreach.reach.channel;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 *
 * <p>The breaker is obtained from a <em>managed</em> {@link CircuitBreakerRegistry} bean rather than a
 * throwaway local registry, so the {@code emailChannel} breaker is discoverable for observability
 * (Micrometer metrics / actuator). When the {@code resilience4j-spring-boot3} starter contributes its
 * own registry that one is used (this config only supplies a default via {@code
 * @ConditionalOnMissingBean}); either way the breaker is registered against a registry the rest of the
 * app can observe.
 */
@Configuration
@EnableConfigurationProperties(EmailChannelProperties.class)
public class EmailChannelConfig {

    /** The named breaker instance guarding the Email provider call. */
    public static final String EMAIL_BREAKER_NAME = "emailChannel";

    /**
     * Falls back to a default {@link CircuitBreakerRegistry} only when the {@code
     * resilience4j-spring-boot3} starter has not already contributed one, so the breaker is always
     * registered against an observable, application-managed registry.
     *
     * @return a managed circuit-breaker registry
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    /**
     * @param registry the managed registry the breaker is registered against (observable via metrics)
     * @param properties the bound Email-channel tunables
     * @return the breaker that {@link EmailAdapter} executes the provider call through
     */
    @Bean
    public CircuitBreaker emailChannelBreaker(CircuitBreakerRegistry registry, EmailChannelProperties properties) {
        return registry.circuitBreaker(EMAIL_BREAKER_NAME, properties.toCircuitBreakerConfig());
    }
}
