package com.example.campaignreach.reach.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates the data-retention policy property (task 11.1, spec「收件人 PII 最小化與抑制名單」scenario「保留策略
 * 存在且可設定」, NFR-005). The retention period must be a present, configurable, strictly-positive value
 * and must NEVER silently default to permanent retention.
 */
class RetentionPropertiesTest {

    @Test
    @DisplayName("保留策略存在且可設定：已設定的保留期限被採用")
    void honoursConfiguredPeriod() {
        RetentionProperties props = new RetentionProperties(Duration.ofDays(395));

        assertThat(props.period()).isEqualTo(Duration.ofDays(395));
    }

    @Test
    @DisplayName("保留策略存在且可設定：未設定時快速失敗（不得預設為永久保留）")
    void rejectsMissingPeriodInsteadOfDefaultingToPermanent() {
        assertThatThrownBy(() -> new RetentionProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retention.period");
    }

    @Test
    @DisplayName("保留策略存在且可設定：非正值（零/負）被拒絕")
    void rejectsNonPositivePeriod() {
        assertThatThrownBy(() -> new RetentionProperties(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetentionProperties(Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
