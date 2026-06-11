package com.example.campaignreach.reach.channel;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Provider-gated registration of {@link EmailAdapter} (task 8.1, spec §4 FR-009).
 *
 * <p>Kept as an {@link AutoConfiguration} — separate from {@link EmailChannelConfig} — on purpose.
 * {@code @ConditionalOnBean} can only see bean definitions registered <em>before</em> it is
 * evaluated, so on a regular {@code @Configuration} it is sensitive to user-config / component-scan
 * ordering (Spring's own docs limit it to auto-configuration classes). Auto-configuration is processed
 * after all user and component-scanned beans, so by the time this condition runs the future
 * SendGrid/SES {@link EmailProviderClient} binding is already registered regardless of scan order, and
 * the adapter activates deterministically. When no provider is bound (the state until that later
 * section lands) no {@code EmailAdapter} bean is registered at all — so there is no phantom bean and
 * no silent no-op EMAIL send.
 *
 * <p>This class is excluded from component scanning by {@code @SpringBootApplication}'s default
 * {@code AutoConfigurationExcludeFilter} (it is listed in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}), so it is
 * registered exactly once.
 */
@AutoConfiguration(after = EmailChannelConfig.class)
public class EmailAdapterAutoConfiguration {

    /**
     * @param providerClient the SendGrid/SES seam — present only once a real binding lands
     * @param emailChannelBreaker the configured breaker from {@link EmailChannelConfig}
     * @return the wired adapter; registered only when an {@link EmailProviderClient} bean exists
     */
    @Bean
    @ConditionalOnBean(EmailProviderClient.class)
    public EmailAdapter emailAdapter(EmailProviderClient providerClient, CircuitBreaker emailChannelBreaker) {
        return new EmailAdapter(providerClient, emailChannelBreaker);
    }
}
