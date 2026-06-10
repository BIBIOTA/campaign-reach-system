package com.example.campaignreach.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Fail-fast acceptance for the Email provider secret (design.md §10): the API
 * key is bound from configuration (env var / vault) and a missing/blank value
 * must fail context startup with a clear validation error — the key is never
 * defaulted in code or version control.
 */
class EmailProviderPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsApiKeyFromConfiguration() {
        contextRunner
                .withPropertyValues("campaignreach.email-provider.api-key=from-vault-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmailProviderProperties.class).getApiKey())
                            .isEqualTo("from-vault-secret");
                });
    }

    @Test
    void startupFailsWhenEmailApiKeyMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            // Root cause is the JSR-380 validation failure carrying the explicit
            // @NotBlank message — not a silent degradation.
            assertThat(context.getStartupFailure())
                    .getRootCause()
                    .isInstanceOf(BindValidationException.class)
                    .hasMessageContaining("apiKey")
                    .hasMessageContaining("EMAIL_PROVIDER_API_KEY");
        });
    }

    @Test
    void startupFailsWhenEmailApiKeyBlank() {
        contextRunner
                .withPropertyValues("campaignreach.email-provider.api-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(EmailProviderProperties.class)
    static class TestConfig {}
}
