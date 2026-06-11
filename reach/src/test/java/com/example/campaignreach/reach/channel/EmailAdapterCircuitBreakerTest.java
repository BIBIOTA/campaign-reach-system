package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.campaignreach.shared.event.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Fast unit tests for {@link EmailAdapter}'s circuit-breaker behaviour (task 8.1; spec §6 NFR-004
 * "外部通道中斷的穩定降級"). The breaker is built from {@link EmailChannelProperties} with a tiny,
 * fast-cooling window so the real Resilience4j state machine can be driven through CLOSED → OPEN →
 * HALF_OPEN → CLOSED/OPEN within a unit test. The provider client is mocked to control success vs.
 * failure; we assert on observable breaker state transitions and the adapter's failure translation,
 * not on config values.
 */
@ExtendWith(MockitoExtension.class)
class EmailAdapterCircuitBreakerTest {

    private static final ReachMessage MESSAGE = new ReachMessage(UUID.randomUUID(), Channel.EMAIL, "welcome");

    @Mock
    private EmailProviderClient providerClient;

    /**
     * @param window count-based sliding window / minimum calls
     * @param threshold failure-rate percentage
     * @param coolDown OPEN-state cool-down
     * @param halfOpenProbes probes admitted in HALF_OPEN
     */
    private static CircuitBreaker breaker(int window, float threshold, Duration coolDown, int halfOpenProbes) {
        EmailChannelProperties props = new EmailChannelProperties(
                new EmailChannelProperties.CircuitBreaker(window, window, threshold, coolDown, halfOpenProbes));
        return io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults()
                .circuitBreaker("test", props.toCircuitBreakerConfig());
    }

    private SendResult okResult() {
        return new SendResult("provider-msg-" + UUID.randomUUID());
    }

    @Nested
    @DisplayName("失敗率達門檻時 breaker 開啟")
    class OpensWhenFailureRateReachesThreshold {

        @Test
        @DisplayName("滑動窗口內失敗率達門檻後 breaker 轉為 OPEN（並從此快速失敗）")
        void breakerTransitionsToOpenOnFailureRateThreshold() {
            // Window of 4 calls, 50% threshold: 2 of 4 failures must trip the breaker.
            CircuitBreaker cb = breaker(4, 50f, Duration.ofSeconds(30), 2);
            EmailAdapter adapter = new EmailAdapter(providerClient, cb);
            // 2 successes then 2 failures => 50% over the full window.
            when(providerClient.deliver(MESSAGE))
                    .thenReturn(okResult())
                    .thenReturn(okResult())
                    .thenThrow(new RuntimeException("provider 503"))
                    .thenThrow(new RuntimeException("provider 503"));

            adapter.send(MESSAGE);
            adapter.send(MESSAGE);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(RetryableSendException.class);
            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(RetryableSendException.class);

            // Threshold reached -> OPEN, and isAvailable() reports unavailable.
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(adapter.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("窗口大小、門檻、最小取樣數皆可由設定調整（低門檻更早開啟）")
        void thresholdAndWindowAreConfigurable() {
            // A stricter 25% threshold over a window of 4 trips after a single failure.
            CircuitBreaker cb = breaker(4, 25f, Duration.ofSeconds(30), 2);
            EmailAdapter adapter = new EmailAdapter(providerClient, cb);
            when(providerClient.deliver(MESSAGE))
                    .thenReturn(okResult())
                    .thenReturn(okResult())
                    .thenReturn(okResult())
                    .thenThrow(new RuntimeException("provider 503"));

            adapter.send(MESSAGE);
            adapter.send(MESSAGE);
            adapter.send(MESSAGE);
            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(RetryableSendException.class);

            // 1/4 = 25% >= 25% threshold -> OPEN.
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("冷卻後以 half-open 探測恢復")
    class RecoversViaHalfOpenProbing {

        @Test
        @DisplayName("冷卻後轉 HALF_OPEN，探測全數成功則轉回 CLOSED 恢復發送")
        void allProbesSucceedReturnsToClosed() throws InterruptedException {
            CircuitBreaker cb = breaker(2, 50f, Duration.ofMillis(50), 2);
            EmailAdapter adapter = new EmailAdapter(providerClient, cb);
            // First two calls fail -> OPEN; subsequent calls (probes) succeed.
            when(providerClient.deliver(MESSAGE))
                    .thenThrow(new RuntimeException("down"))
                    .thenThrow(new RuntimeException("down"))
                    .thenReturn(okResult());

            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(RetryableSendException.class);
            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(RetryableSendException.class);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            Thread.sleep(80); // wait out the cool-down so OPEN -> HALF_OPEN is permitted
            // Two successful probes (permitted half-open = 2) must close the breaker.
            adapter.send(MESSAGE);
            adapter.send(MESSAGE);

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(adapter.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("任一探測失敗則重新回到 OPEN 並重啟冷卻")
        void anyProbeFailureReopensBreaker() throws InterruptedException {
            CircuitBreaker cb = breaker(2, 50f, Duration.ofMillis(50), 2);
            EmailAdapter adapter = new EmailAdapter(providerClient, cb);
            when(providerClient.deliver(MESSAGE))
                    .thenThrow(new RuntimeException("down")) // trip 1
                    .thenThrow(new RuntimeException("down")) // trip 2 -> OPEN
                    .thenReturn(okResult()) // probe 1 ok
                    .thenThrow(new RuntimeException("still down")); // probe 2 fails -> reopen

            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(RetryableSendException.class);
            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(RetryableSendException.class);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            Thread.sleep(80);
            adapter.send(MESSAGE); // probe 1 succeeds
            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(RetryableSendException.class); // probe 2 fails

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("breaker 開啟時不卡任務")
    class BreakerOpenDoesNotBlockTasks {

        @Test
        @DisplayName("breaker OPEN 時 isAvailable() 回報不可用，呼叫快速失敗為 breakerOpen retryable（dispatcher 跳過、任務維持 PENDING）")
        void openBreakerFastFailsAsBreakerOpenRetryable() {
            CircuitBreaker cb = breaker(2, 50f, Duration.ofSeconds(30), 2);
            EmailAdapter adapter = new EmailAdapter(providerClient, cb);
            when(providerClient.deliver(MESSAGE))
                    .thenThrow(new RuntimeException("down"))
                    .thenThrow(new RuntimeException("down"));

            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(RetryableSendException.class);
            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(RetryableSendException.class);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Dispatcher seam: query availability BEFORE claiming -> reports unavailable, so it skips.
            assertThat(adapter.isAvailable()).isFalse();
            // If it does attempt a send anyway, the breaker short-circuits (provider untouched) and the
            // failure is flagged breakerOpen so the dispatcher can leave the task PENDING.
            assertThatThrownBy(() -> adapter.send(MESSAGE))
                    .isInstanceOf(RetryableSendException.class)
                    .matches(ex -> ((RetryableSendException) ex).isBreakerOpen(), "isBreakerOpen");
        }
    }

    @Nested
    @DisplayName("已 PROCESSING 後 breaker 失敗")
    class FailureAfterProcessing {

        @Test
        @DisplayName("breaker 在外部呼叫快速失敗時拋可重試例外（dispatcher 比照走 RETRY_SCHEDULED，不卡在 PROCESSING）")
        void midSendFailureIsRetryableNotStuck() {
            CircuitBreaker cb = breaker(8, 50f, Duration.ofSeconds(30), 2);
            EmailAdapter adapter = new EmailAdapter(providerClient, cb);
            // Breaker still CLOSED; the provider call itself fails fast mid-send.
            when(providerClient.deliver(MESSAGE)).thenThrow(new RuntimeException("provider timeout"));

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThatThrownBy(() -> adapter.send(MESSAGE))
                    .isInstanceOf(RetryableSendException.class)
                    // A real provider failure (not a breaker short-circuit) -> retryable, dispatcher
                    // write-back to RETRY_SCHEDULED rather than stuck in PROCESSING.
                    .matches(ex -> !((RetryableSendException) ex).isBreakerOpen(), "providerFailure");
        }
    }

    @Nested
    @DisplayName("基本契約")
    class BasicContract {

        @Test
        @DisplayName("成功送出回傳 provider message id，channel() 為 EMAIL")
        void successReturnsResultAndChannelIsEmail() {
            CircuitBreaker cb = breaker(8, 50f, Duration.ofSeconds(30), 2);
            EmailAdapter adapter = new EmailAdapter(providerClient, cb);
            SendResult expected = okResult();
            when(providerClient.deliver(MESSAGE)).thenReturn(expected);

            assertThat(adapter.channel()).isEqualTo(Channel.EMAIL);
            assertThat(adapter.send(MESSAGE)).isEqualTo(expected);
        }

        @Test
        @DisplayName("非 EMAIL 訊息被拒（adapter 只處理自己的 channel）")
        void rejectsNonEmailMessage() {
            CircuitBreaker cb = breaker(8, 50f, Duration.ofSeconds(30), 2);
            EmailAdapter adapter = new EmailAdapter(providerClient, cb);
            lenient()
                    .when(providerClient.deliver(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(okResult());
            ReachMessage smsMessage = new ReachMessage(UUID.randomUUID(), Channel.SMS, "welcome");

            assertThatThrownBy(() -> adapter.send(smsMessage)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
