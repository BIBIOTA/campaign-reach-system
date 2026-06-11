package com.example.campaignreach.reach.channel;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
 * <p>The breaker is obtained from the {@link CircuitBreakerRegistry} contributed by the {@code
 * resilience4j-spring-boot3} auto-configuration (Micrometer metrics / actuator enabled). No fallback
 * registry is defined here — a {@code @ConditionalOnMissingBean} in a regular {@code @Configuration}
 * would be evaluated before auto-configuration and would silently disable the starter's full wiring.
 *
 * <p>{@link EmailAdapter} bean registration is also controlled here via
 * {@code @Bean @ConditionalOnBean(EmailProviderClient.class)} to avoid the non-deterministic
 * component-scan ordering that results from placing {@code @ConditionalOnBean} on a {@code @Component}.
 */
@Configuration
@EnableConfigurationProperties(EmailChannelProperties.class)
public class EmailChannelConfig {

    /** The named breaker instance guarding the Email provider call. */
    public static final String EMAIL_BREAKER_NAME = "emailChannel";

    /**
     * @param registry the auto-configured registry (from resilience4j-spring-boot3) — metrics and
     *     actuator integration are fully active
     * @param properties the bound Email-channel tunables
     * @return the breaker that {@link EmailAdapter} executes the provider call through
     */
    @Bean
    public CircuitBreaker emailChannelBreaker(CircuitBreakerRegistry registry, EmailChannelProperties properties) {
        return registry.circuitBreaker(EMAIL_BREAKER_NAME, properties.toCircuitBreakerConfig());
    }

    /**
     * Registers {@link EmailAdapter} only when an {@link EmailProviderClient} bean is present.
     * Declared here (not via {@code @Component @ConditionalOnBean}) so the condition is evaluated
     * after all bean definitions are loaded, avoiding non-deterministic component-scan ordering.
     */
    @Bean
    @ConditionalOnBean(EmailProviderClient.class)
    public EmailAdapter emailAdapter(EmailProviderClient providerClient, CircuitBreaker emailChannelBreaker) {
        return new EmailAdapter(providerClient, emailChannelBreaker);
    }
}
