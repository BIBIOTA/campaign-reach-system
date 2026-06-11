package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Non-Docker context-load guard for the Email channel wiring (task 8.1).
 *
 * <p>Verifies that {@link EmailChannelConfig} starts cleanly without a concrete {@link
 * EmailProviderClient} binding, and that the {@link EmailAdapter} activates only once one is
 * present. {@code EmailAdapter} registration is controlled by {@code @Bean @ConditionalOnBean} in
 * {@link EmailChannelConfig} (not {@code @Component}), so condition evaluation is deterministic.
 * This test is intentionally NOT Docker-gated so CI catches regressions even when the full
 * {@code @SpringBootTest} suite is skipped.
 */
class EmailChannelContextLoadTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(EmailChannelConfig.class, TestRegistryConfig.class));

    @Test
    @DisplayName("無 EmailProviderClient bean 時 context 正常啟動，且不註冊 EmailAdapter（修復不可滿足依賴的啟動失敗）")
    void contextStartsWithoutProviderAndAdapterIsAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            // The breaker + registry are wired (observability), but the adapter stays out.
            assertThat(context).hasSingleBean(CircuitBreaker.class);
            assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
            assertThat(context).doesNotHaveBean(EmailAdapter.class);
            assertThat(context).doesNotHaveBean(EmailProviderClient.class);
        });
    }

    @Test
    @DisplayName("一旦提供 EmailProviderClient bean，EmailAdapter 才被啟用")
    void adapterActivatesOnceProviderBeanIsPresent() {
        // StubProvider is listed first so its EmailProviderClient bean is registered before
        // EmailChannelConfig processes its @Bean @ConditionalOnBean(EmailProviderClient.class).
        new ApplicationContextRunner()
                .withConfiguration(
                        UserConfigurations.of(StubProvider.class, EmailChannelConfig.class, TestRegistryConfig.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmailProviderClient.class);
                    assertThat(context).hasSingleBean(EmailAdapter.class);
                });
    }

    /**
     * Provides a fallback {@link CircuitBreakerRegistry} for the isolated context-load test.
     * In production, resilience4j-spring-boot3 auto-configuration supplies the registry;
     * here there is no auto-configuration, so the test must supply its own.
     * {@link EmailAdapter} registration is handled by {@link EmailChannelConfig} itself.
     */
    @Configuration
    static class TestRegistryConfig {

        @Bean
        CircuitBreakerRegistry circuitBreakerRegistry() {
            return CircuitBreakerRegistry.ofDefaults();
        }
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
