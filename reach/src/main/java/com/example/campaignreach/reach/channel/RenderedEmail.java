package com.example.campaignreach.reach.channel;

/**
 * The rendered subject and body produced by {@link LocalEmailTemplateRenderer} for a single local
 * smoke-test email (design.md §信件樣板).
 *
 * <p>This is an intentionally minimal value type (YAGNI): it carries only what the local SMTP provider
 * needs to compose an outgoing message — there is no HTML/plain split, no headers, and no recipient,
 * because the local renderer exists purely to make a smoke-test send observable, not to model a real
 * marketing template. The recipient stays out of this type for the same PII-minimization reason
 * {@link ReachMessage} omits the email address: contact data is resolved by the provider at send time.
 *
 * @param subject the email subject line (non-blank)
 * @param body the email body text (non-blank)
 */
public record RenderedEmail(String subject, String body) {

    /** Validates that the renderer never emits a blank subject or body. */
    public RenderedEmail {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
    }
}
