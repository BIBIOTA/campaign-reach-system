package com.example.campaignreach.reach.channel;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable circuit-breaker tunables for the Email channel (task 8.1, spec §6 NFR-004), bound
 * from {@code campaignreach.reach.email.circuit-breaker.*}.
 *
 * <p>The defaults encode the spec's stated values so they are visible and overridable in one place
 * (rather than buried in Resilience4j config): sliding window 20, failure-rate threshold 50%,
 * minimum 20 calls before the rate is evaluated, 30s cool-down in OPEN, and 5 permitted probes in
 * HALF_OPEN. The {@link #toCircuitBreakerConfig()} factory turns these into the Resilience4j config
 * that {@code EmailChannelConfig} registers.
 *
 * @param circuitBreaker the breaker knobs ({@code campaignreach.reach.email.circuit-breaker.*})
 */
@ConfigurationProperties(prefix = "campaignreach.reach.email")
public record EmailChannelProperties(CircuitBreaker circuitBreaker) {

    /** Applies the NFR-004 defaults so the breaker is usable without explicit configuration. */
    public EmailChannelProperties {
        circuitBreaker = circuitBreaker == null ? new CircuitBreaker(null, null, null, null, null) : circuitBreaker;
    }

    /**
     * @return a Resilience4j {@link CircuitBreakerConfig} built from these tunables, using a
     *     count-based sliding window so the window size is "last N calls" exactly as the spec
     *     describes.
     */
    public CircuitBreakerConfig toCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(circuitBreaker.slidingWindowSize())
                .minimumNumberOfCalls(circuitBreaker.minimumNumberOfCalls())
                .failureRateThreshold(circuitBreaker.failureRateThreshold())
                .waitDurationInOpenState(circuitBreaker.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(circuitBreaker.permittedNumberOfCallsInHalfOpenState())
                // Move OPEN -> HALF_OPEN automatically once the cool-down elapses, via Resilience4j's
                // background scheduler, so recovery does not depend on an inbound call arriving to
                // trigger the transition.
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
    }

    /**
     * Circuit-breaker knobs with NFR-004 defaults.
     *
     * @param slidingWindowSize number of most-recent calls the failure rate is computed over (count
     *     based; default 20)
     * @param minimumNumberOfCalls minimum recorded calls before the failure rate is evaluated
     *     (default 20)
     * @param failureRateThreshold failure-rate percentage that trips the breaker to OPEN (default 50)
     * @param waitDurationInOpenState cool-down spent in OPEN before probing (default 30s)
     * @param permittedNumberOfCallsInHalfOpenState probes admitted in HALF_OPEN (default 5)
     */
    public record CircuitBreaker(
            Integer slidingWindowSize,
            Integer minimumNumberOfCalls,
            Float failureRateThreshold,
            Duration waitDurationInOpenState,
            Integer permittedNumberOfCallsInHalfOpenState) {

        /** Applies and validates the NFR-004 defaults. */
        public CircuitBreaker {
            slidingWindowSize = positive(slidingWindowSize, 20, "sliding-window-size");
            minimumNumberOfCalls = positive(minimumNumberOfCalls, 20, "minimum-number-of-calls");
            failureRateThreshold = rate(failureRateThreshold);
            waitDurationInOpenState =
                    waitDurationInOpenState == null ? Duration.ofSeconds(30) : waitDurationInOpenState;
            if (waitDurationInOpenState.isNegative() || waitDurationInOpenState.isZero()) {
                throw new IllegalArgumentException(
                        "campaignreach.reach.email.circuit-breaker.wait-duration-in-open-state must be positive");
            }
            permittedNumberOfCallsInHalfOpenState =
                    positive(permittedNumberOfCallsInHalfOpenState, 5, "permitted-number-of-calls-in-half-open-state");
        }

        private static int positive(Integer value, int fallback, String knob) {
            int resolved = value == null ? fallback : value;
            if (resolved <= 0) {
                throw new IllegalArgumentException(
                        "campaignreach.reach.email.circuit-breaker." + knob + " must be positive");
            }
            return resolved;
        }

        private static float rate(Float value) {
            float resolved = value == null ? 50f : value;
            if (resolved <= 0f || resolved > 100f) {
                throw new IllegalArgumentException(
                        "campaignreach.reach.email.circuit-breaker.failure-rate-threshold must be in (0, 100]");
            }
            return resolved;
        }
    }
}
