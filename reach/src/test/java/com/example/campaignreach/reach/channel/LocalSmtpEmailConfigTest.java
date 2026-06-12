package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Non-Docker context guard for the local SMTP provider wiring (task 1.2).
 *
 * <p>Proves the double-gating of {@link LocalSmtpEmailConfig} ({@code @Profile("local")} +
 * {@code campaignreach.email-provider.mode=smtp-local}) and that, once the local provider bean is
 * present, the existing provider-gated {@link EmailAdapter} activates. The full production wiring chain
 * is reproduced via {@link AutoConfigurations} (real {@code resilience4j-spring-boot3}
 * {@link CircuitBreakerAutoConfiguration} plus {@link EmailAdapterAutoConfiguration}) and
 * {@link EmailChannelConfig} (provides the {@code emailChannelBreaker} the adapter needs), so the
 * {@code @ConditionalOnBean} adapter activation is evaluated in the same (last) phase as in production.
 * Intentionally NOT Docker-gated — it uses {@link ApplicationContextRunner}, no real SMTP/DB.
 */
class LocalSmtpEmailConfigTest {

    private static final String[] FULL_LOCAL_SETTINGS = {
        "campaignreach.email-provider.local.host=localhost",
        "campaignreach.email-provider.local.port=1025",
        "campaignreach.email-provider.local.from=noreply@local.test",
        "campaignreach.email-provider.local.recipient=smoke@local.test",
        "campaignreach.email-provider.local.timeout=5s",
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(CircuitBreakerAutoConfiguration.class, EmailAdapterAutoConfiguration.class))
            .withConfiguration(UserConfigurations.of(EmailChannelConfig.class, LocalSmtpEmailConfig.class));

    @Test
    @DisplayName("非 local profile 時不註冊本機 EmailProviderClient，且 EmailAdapter 不啟用")
    void localProviderIsDisabledOutsideExplicitLocalMode() {
        // No "local" profile active and mode unset: the whole LocalSmtpEmailConfig is gated off.
        runner.withPropertyValues(FULL_LOCAL_SETTINGS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(EmailProviderClient.class);
            assertThat(context).doesNotHaveBean(EmailAdapter.class);
        });
    }

    @Test
    @DisplayName("local profile 啟用但 mode 非 smtp-local 時，不註冊本機 EmailProviderClient")
    void localProviderIsDisabledWhenModeDoesNotMatch() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues(FULL_LOCAL_SETTINGS)
                .withPropertyValues("campaignreach.email-provider.mode=disabled")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(EmailProviderClient.class);
                    assertThat(context).doesNotHaveBean(EmailAdapter.class);
                });
    }

    @Test
    @DisplayName("local profile + mode=smtp-local + 完整設定時，註冊本機 EmailProviderClient 並啟用 EmailAdapter")
    void localProviderActivatesWhenLocalModeIsComplete() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues(FULL_LOCAL_SETTINGS)
                .withPropertyValues("campaignreach.email-provider.mode=smtp-local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmailProviderClient.class);
                    assertThat(context.getBean(EmailProviderClient.class))
                            .isInstanceOf(LocalSmtpEmailProviderClient.class);
                    // The existing provider-gated EmailAdapter activates because the local provider bean is
                    // registered (component-scan / user-config phase) before EmailAdapterAutoConfiguration's
                    // @ConditionalOnBean is evaluated in the auto-config phase.
                    assertThat(context).hasSingleBean(EmailAdapter.class);
                });
    }
}
