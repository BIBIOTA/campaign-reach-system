package com.example.campaignreach.reach.channel;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local SMTP settings for the local-mode email provider used by local smoke tests, bound from {@code
 * campaignreach.email-provider.local.*}.
 *
 * <p>These values are deliberately validated <em>fail-fast</em> in the compact constructor so an
 * invalid local SMTP configuration aborts context startup (during configuration binding / bean
 * creation) rather than silently registering a half-configured provider that would only fail at
 * dispatch time. There are intentionally <strong>no defaults</strong>: host, from, and recipient are
 * environment-specific and a wrong-but-present default could let a local smoke run leak mail to an
 * unintended address, so each must be supplied explicitly. This mirrors the existing fail-fast,
 * property-path-named validation style of {@link EmailChannelProperties}.
 *
 * <p>Activation/registration of the actual provider bean (and wiring this type via {@code
 * @EnableConfigurationProperties}) is a separate task; this type only declares and validates the
 * bindable settings.
 *
 * @param host SMTP server host the local provider connects to (required, non-blank)
 * @param port SMTP server TCP port (required, must be a valid port in {@code 1..65535})
 * @param from the envelope/from email address local mail is sent as (required, non-blank)
 * @param recipient the fixed recipient address local smoke mail is delivered to (required, non-blank)
 * @param timeout connect/read timeout for the SMTP exchange (required, must be strictly positive)
 */
@ConfigurationProperties(prefix = "campaignreach.email-provider.local")
public record LocalSmtpEmailProperties(String host, Integer port, String from, String recipient, Duration timeout) {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    /** Validates the local SMTP settings, failing fast with the offending property path. */
    public LocalSmtpEmailProperties {
        requireText(host, "host");
        requireText(from, "from");
        requireText(recipient, "recipient");
        requirePort(port);
        requirePositive(timeout);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("campaignreach.email-provider.local." + field + " must not be blank");
        }
    }

    private static void requirePort(Integer value) {
        if (value == null || value < MIN_PORT || value > MAX_PORT) {
            throw new IllegalArgumentException(
                    "campaignreach.email-provider.local.port must be a valid TCP port in [1, 65535]");
        }
    }

    private static void requirePositive(Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("campaignreach.email-provider.local.timeout must be positive");
        }
    }
}
