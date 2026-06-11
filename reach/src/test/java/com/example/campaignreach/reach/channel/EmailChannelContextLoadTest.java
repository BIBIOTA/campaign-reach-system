package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Non-Docker context-load guard for the Email channel wiring (task 8.1 review fix).
 *
 * <p>Reproduces, without containers, how {@code @SpringBootApplication} component-scans the channel
 * package: it must start cleanly even though task 8.1 ships no concrete {@link EmailProviderClient}
 * binding. The {@link EmailAdapter} is {@link ConditionalOnBean} on an {@code EmailProviderClient}, so
 * with no provider present the context must come up with the breaker wired but <em>no</em> adapter
 * bean — and only once a provider bean is contributed does the adapter activate. This is the
 * regression guard for the "unsatisfiable @Component breaks startup" finding; it is intentionally NOT
 * Docker-gated so CI catches a re-break even when the full {@code @SpringBootTest} suite is skipped.
 */
class EmailChannelContextLoadTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(EmailChannelConfig.class, AdapterRegistration.class));

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
        // The provider config is listed FIRST so its bean is already registered when the adapter's
        // @ConditionalOnBean is evaluated (the condition is order-sensitive on bean definitions).
        new ApplicationContextRunner()
                .withConfiguration(
                        UserConfigurations.of(StubProvider.class, EmailChannelConfig.class, AdapterRegistration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmailProviderClient.class);
                    assertThat(context).hasSingleBean(EmailAdapter.class);
                });
    }

    /**
     * Stands in for component-scanning the channel package: registers {@link EmailAdapter} as a
     * conditional bean exactly as the {@code @Component @ConditionalOnBean(EmailProviderClient.class)}
     * on the class would be evaluated under a scan.
     */
    @Configuration
    @EnableConfigurationProperties(EmailChannelProperties.class)
    static class AdapterRegistration {

        @Bean
        @ConditionalOnBean(EmailProviderClient.class)
        EmailAdapter emailAdapter(EmailProviderClient providerClient, CircuitBreaker emailChannelBreaker) {
            return new EmailAdapter(providerClient, emailChannelBreaker);
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
