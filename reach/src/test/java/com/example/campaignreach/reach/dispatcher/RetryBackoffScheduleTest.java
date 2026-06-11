package com.example.campaignreach.reach.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the bounded exponential-backoff schedule (task 9.1, scenario「可重試失敗走指數退避」
 * + the「最多 3 次」bound). No DB or clock; just the {@code 1m → 5m → 30m} delays and the
 * max-attempts terminal condition.
 */
class RetryBackoffScheduleTest {

    @Test
    @DisplayName("可重試失敗走指數退避：退避為 1m → 5m → 30m")
    void backoffScheduleIsOneFiveThirtyMinutes() {
        assertThat(RetryBackoffSchedule.backoffFor(0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(RetryBackoffSchedule.backoffFor(1)).isEqualTo(Duration.ofMinutes(5));
        assertThat(RetryBackoffSchedule.backoffFor(2)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("最多 3 次：前三次可重試，第三次後耗盡")
    void allowsThreeRetriesThenExhausts() {
        assertThat(RetryBackoffSchedule.canRetry(0)).isTrue();
        assertThat(RetryBackoffSchedule.canRetry(1)).isTrue();
        assertThat(RetryBackoffSchedule.canRetry(2)).isTrue();
        assertThat(RetryBackoffSchedule.canRetry(3)).isFalse();
        assertThat(RetryBackoffSchedule.MAX_ATTEMPTS).isEqualTo(3);
    }

    @Test
    @DisplayName("耗盡後請求退避會拋出（呼叫端須先檢查 canRetry）")
    void backoffAfterExhaustionThrows() {
        assertThatThrownBy(() -> RetryBackoffSchedule.backoffFor(3)).isInstanceOf(IllegalStateException.class);
    }
}
