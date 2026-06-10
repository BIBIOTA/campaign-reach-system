package com.example.campaignreach.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds the Email provider secret (API key) from configuration (design.md §10).
 *
 * <p>The key MUST come from a secret manager (environment variable / vault) and
 * MUST NOT be stored in the database or committed to version control. The
 * {@code application.yml} placeholder {@code ${EMAIL_PROVIDER_API_KEY}} resolves
 * this value at startup; no real default is provided.
 *
 * <p>Fail-fast: {@code @Validated} + {@link NotBlank} make the application
 * context fail to start with a clear binding error when the secret is missing,
 * rather than silently degrading.
 */
@Validated
@ConfigurationProperties(prefix = "campaignreach.email-provider")
public class EmailProviderProperties {

    /**
     * Email provider API key. Supplied via the {@code EMAIL_PROVIDER_API_KEY}
     * environment variable / vault — never hard-coded, never version-controlled.
     */
    @NotBlank(message = "campaignreach.email-provider.api-key must be provided "
            + "via the EMAIL_PROVIDER_API_KEY environment variable / vault and must not be blank")
    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
