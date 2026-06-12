package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the fail-fast binding/validation of {@link LocalSmtpEmailProperties} (task 1.1). Covers
 * the scenario "Invalid local SMTP configuration fails startup": a complete, valid configuration
 * binds, while a missing host/from/recipient, an out-of-range port, or a non-positive timeout each
 * throws {@link IllegalArgumentException} naming the offending {@code
 * campaignreach.email-provider.local.*} property path.
 */
class LocalSmtpEmailPropertiesTest {

    private static LocalSmtpEmailProperties valid() {
        return new LocalSmtpEmailProperties(
                "localhost", 1025, "noreply@example.com", "smoke@example.com", Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("完整且合法的本機 SMTP 設定可成功綁定")
    void bindsValidConfiguration() {
        LocalSmtpEmailProperties props = valid();

        assertThat(props.host()).isEqualTo("localhost");
        assertThat(props.port()).isEqualTo(1025);
        assertThat(props.from()).isEqualTo("noreply@example.com");
        assertThat(props.recipient()).isEqualTo("smoke@example.com");
        assertThat(props.timeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("非法本機 SMTP 設定快速失敗：缺 host/from/recipient、port 越界、timeout 非正皆中止啟動")
    void invalidLocalSmtpConfigurationFailsStartup() {
        // Missing host (null and blank).
        assertThatThrownBy(() -> new LocalSmtpEmailProperties(
                        null, 1025, "from@example.com", "to@example.com", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaignreach.email-provider.local.host");
        assertThatThrownBy(() -> new LocalSmtpEmailProperties(
                        "  ", 1025, "from@example.com", "to@example.com", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaignreach.email-provider.local.host");

        // Missing from.
        assertThatThrownBy(() ->
                        new LocalSmtpEmailProperties("localhost", 1025, null, "to@example.com", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaignreach.email-provider.local.from");

        // Missing recipient.
        assertThatThrownBy(() -> new LocalSmtpEmailProperties(
                        "localhost", 1025, "from@example.com", "  ", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaignreach.email-provider.local.recipient");

        // Port out of range (null, below 1, above 65535).
        assertThatThrownBy(() -> new LocalSmtpEmailProperties(
                        "localhost", null, "from@example.com", "to@example.com", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaignreach.email-provider.local.port");
        assertThatThrownBy(() -> new LocalSmtpEmailProperties(
                        "localhost", 0, "from@example.com", "to@example.com", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaignreach.email-provider.local.port");
        assertThatThrownBy(() -> new LocalSmtpEmailProperties(
                        "localhost", 70000, "from@example.com", "to@example.com", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaignreach.email-provider.local.port");

        // Timeout non-positive (null, zero, negative).
        assertThatThrownBy(() ->
                        new LocalSmtpEmailProperties("localhost", 1025, "from@example.com", "to@example.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaignreach.email-provider.local.timeout");
        assertThatThrownBy(() -> new LocalSmtpEmailProperties(
                        "localhost", 1025, "from@example.com", "to@example.com", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaignreach.email-provider.local.timeout");
        assertThatThrownBy(() -> new LocalSmtpEmailProperties(
                        "localhost", 1025, "from@example.com", "to@example.com", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaignreach.email-provider.local.timeout");
    }
}
