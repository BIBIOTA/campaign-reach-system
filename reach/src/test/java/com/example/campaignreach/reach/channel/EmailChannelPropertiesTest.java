package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the NFR-004 circuit-breaker defaults and validation of {@link EmailChannelProperties}
 * (task 8.1, spec §6). The defaults must match the spec's stated values and feed through to the
 * Resilience4j {@link CircuitBreakerConfig}.
 */
class EmailChannelPropertiesTest {

    @Test
    @DisplayName("缺省值符合 NFR-004：窗口 20、門檻 50%、最小取樣 20、冷卻 30s、half-open 5")
    void appliesDocumentedDefaults() {
        EmailChannelProperties props = new EmailChannelProperties(null);
        EmailChannelProperties.CircuitBreaker cb = props.circuitBreaker();

        assertThat(cb.slidingWindowSize()).isEqualTo(20);
        assertThat(cb.minimumNumberOfCalls()).isEqualTo(20);
        assertThat(cb.failureRateThreshold()).isEqualTo(50f);
        assertThat(cb.waitDurationInOpenState()).isEqualTo(Duration.ofSeconds(30));
        assertThat(cb.permittedNumberOfCallsInHalfOpenState()).isEqualTo(5);

        CircuitBreakerConfig config = props.toCircuitBreakerConfig();
        assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(config.getSlidingWindowSize()).isEqualTo(20);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(20);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(5);
    }

    @Test
    @DisplayName("覆寫值生效（皆可由設定調整）")
    void honoursOverrides() {
        EmailChannelProperties props = new EmailChannelProperties(
                new EmailChannelProperties.CircuitBreaker(10, 5, 60f, Duration.ofSeconds(15), 3));
        EmailChannelProperties.CircuitBreaker cb = props.circuitBreaker();

        assertThat(cb.slidingWindowSize()).isEqualTo(10);
        assertThat(cb.minimumNumberOfCalls()).isEqualTo(5);
        assertThat(cb.failureRateThreshold()).isEqualTo(60f);
        assertThat(cb.waitDurationInOpenState()).isEqualTo(Duration.ofSeconds(15));
        assertThat(cb.permittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
    }

    @Test
    @DisplayName("非法設定快速失敗（門檻須在 (0,100]、其餘須為正）")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> new EmailChannelProperties.CircuitBreaker(0, 20, 50f, Duration.ofSeconds(30), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailChannelProperties.CircuitBreaker(20, 20, 150f, Duration.ofSeconds(30), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailChannelProperties.CircuitBreaker(20, 20, 50f, Duration.ZERO, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
