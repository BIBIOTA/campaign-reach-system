package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Non-Docker context-load guard for the Email channel wiring (task 8.1).
 *
 * <p>Runs against the <em>real</em> {@code resilience4j-spring-boot3}
 * {@link CircuitBreakerAutoConfiguration} (not a hand-rolled registry), so this test exercises the
 * exact production wiring path: the breaker must come from the auto-configured
 * {@link CircuitBreakerRegistry} — the one Micrometer metrics and the actuator health indicator
 * observe — rather than a discarded local registry. {@link AutoConfigurations} also reproduces the
 * production evaluation order (auto-config is processed <em>after</em> user configuration), so the
 * {@code @Bean @ConditionalOnBean} adapter registration is verified under realistic conditions.
 *
 * <p>It verifies that the wiring starts cleanly without a concrete {@link EmailProviderClient}
 * binding (adapter absent, no phantom bean), and that the {@link EmailAdapter} activates once one is
 * present. The provider-gated adapter lives in {@link EmailAdapterAutoConfiguration}, loaded here as a
 * real auto-configuration so its {@code @ConditionalOnBean} is evaluated in the same (last) phase as
 * in production. This test is intentionally NOT Docker-gated so CI catches regressions even when the
 * full {@code @SpringBootTest} suite is skipped.
 */
class EmailChannelContextLoadTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(CircuitBreakerAutoConfiguration.class, EmailAdapterAutoConfiguration.class))
            .withConfiguration(UserConfigurations.of(EmailChannelConfig.class));

    @Test
    @DisplayName("無 EmailProviderClient bean 時 context 正常啟動，且不註冊 EmailAdapter（修復不可滿足依賴的啟動失敗）")
    void contextStartsWithoutProviderAndAdapterIsAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            // The breaker is wired from the auto-configured registry (observable via Micrometer /
            // actuator); the adapter stays out until a provider is present.
            assertThat(context).hasSingleBean(CircuitBreaker.class);
            assertThat(context).doesNotHaveBean(EmailAdapter.class);
            assertThat(context).doesNotHaveBean(EmailProviderClient.class);

            // The breaker must be registered in the auto-configured registry — not a discarded local
            // one — so it is discoverable for metrics/health. This is the regression the Gemini-review
            // fix (commit 132d46a) introduced and previously had no test protection.
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
            assertThat(registry.find(EmailChannelConfig.EMAIL_BREAKER_NAME))
                    .as("emailChannel breaker must live in the auto-configured registry")
                    .isPresent();
        });
    }

    @Test
    @DisplayName("一旦提供 EmailProviderClient bean，EmailAdapter 才被啟用（不依賴 config 註冊順序）")
    void adapterActivatesOnceProviderBeanIsPresent() {
        // StubProvider is listed AFTER EmailChannelConfig on purpose: the adapter's
        // @ConditionalOnBean lives in EmailAdapterAutoConfiguration, evaluated in the auto-config
        // phase (after every user bean is registered), so activation does not depend on user-config
        // ordering.
        runner.withConfiguration(UserConfigurations.of(StubProvider.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EmailProviderClient.class);
            assertThat(context).hasSingleBean(EmailAdapter.class);
        });
    }

    /** Supplies a (mock) provider bean so the conditional adapter activates. */
    @Configuration
    static class StubProvider {

        @Bean
        EmailProviderClient emailProviderClient() {
            return mock(EmailProviderClient.class);
        }
    }
}
