package com.example.campaignreach.reach.channel;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * A local-only {@link EmailProviderClient} that delivers EMAIL reach tasks through Spring Mail to a
 * local SMTP sink (Mailpit) so a send becomes observable during local smoke tests (design.md §本機
 * SMTP Email provider; spec requirement「本機 SMTP Email provider shall deliver EMAIL reach tasks to
 * Mailpit」).
 *
 * <p>It implements the {@link EmailProviderClient} seam exactly as {@link EmailAdapter} expects: it
 * renders a deterministic local test email via {@link LocalEmailTemplateRenderer}, then sends it from
 * the configured from-address to the single fixed local recipient (both from {@link
 * LocalSmtpEmailProperties}). The sequence-diagram contract is {@code render(message, now)} →
 * {@code MailSender.send(from, fixed recipient, subject, body)} → {@code SendResult(providerMessageId)}.
 *
 * <p><strong>Failure contract.</strong> Spring's {@link JavaMailSender} surfaces transport problems
 * (Mailpit unavailable, SMTP timeout, transient transport error) as a {@link MailException}, an
 * unchecked exception. This client deliberately does <em>not</em> catch it: per {@link
 * EmailProviderClient}'s Javadoc, a transient failure must propagate as a plain {@code RuntimeException}
 * so {@link EmailAdapter} translates it into a {@link RetryableSendException} and the circuit breaker
 * counts it. {@link NonRetryableSendException} is reserved for permanent failures, which a local SMTP
 * sink never raises, so this client never throws it.
 *
 * <p><strong>No retry/breaker logic lives here</strong> — backoff and degradation remain {@link
 * EmailAdapter}'s and the dispatcher's responsibility.
 *
 * <p><strong>Spring-free by design.</strong> This is a plain class with no bean annotation; its
 * profile/mode-gated registration is the local provider configuration's job, keeping it off the
 * default component scan and trivially unit-testable with a mock {@link JavaMailSender}.
 *
 * <p><strong>PII discipline.</strong> This client does not log the email body, subject, from-address, or
 * the recipient address. The single conservative success log is limited to non-PII identifiers: the
 * provider message id plus the {@code userId} and {@code templateRef} routing identifiers.
 */
public final class LocalSmtpEmailProviderClient implements EmailProviderClient {

    private static final Logger LOG = LoggerFactory.getLogger(LocalSmtpEmailProviderClient.class);

    /** Marks a provider message id as locally generated (no real SMTP message id is available). */
    private static final String LOCAL_MESSAGE_ID_PREFIX = "local-smtp-";

    private final JavaMailSender mailSender;
    private final LocalEmailTemplateRenderer renderer;
    private final LocalSmtpEmailProperties properties;
    private final Clock clock;
    private final Supplier<String> messageIdSupplier;

    /**
     * Creates a local SMTP provider client with a default {@code local-smtp-<uuid>} message-id supplier.
     *
     * @param mailSender the Spring Mail sender bound to the local SMTP (Mailpit) host/port
     * @param renderer the local smoke-test template renderer
     * @param properties the local SMTP settings supplying the from-address and fixed recipient
     * @param clock the clock used to stamp the render time (injected for deterministic tests)
     */
    public LocalSmtpEmailProviderClient(
            JavaMailSender mailSender,
            LocalEmailTemplateRenderer renderer,
            LocalSmtpEmailProperties properties,
            Clock clock) {
        this(mailSender, renderer, properties, clock, () -> LOCAL_MESSAGE_ID_PREFIX + UUID.randomUUID());
    }

    /**
     * Creates a local SMTP provider client with an explicit message-id supplier (for testability).
     *
     * @param mailSender the Spring Mail sender bound to the local SMTP (Mailpit) host/port
     * @param renderer the local smoke-test template renderer
     * @param properties the local SMTP settings supplying the from-address and fixed recipient
     * @param clock the clock used to stamp the render time (injected for deterministic tests)
     * @param messageIdSupplier supplies the locally generated provider message id (must be non-blank)
     */
    public LocalSmtpEmailProviderClient(
            JavaMailSender mailSender,
            LocalEmailTemplateRenderer renderer,
            LocalSmtpEmailProperties properties,
            Clock clock,
            Supplier<String> messageIdSupplier) {
        if (mailSender == null) {
            throw new IllegalArgumentException("mailSender must not be null");
        }
        if (renderer == null) {
            throw new IllegalArgumentException("renderer must not be null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (messageIdSupplier == null) {
            throw new IllegalArgumentException("messageIdSupplier must not be null");
        }
        this.mailSender = mailSender;
        this.renderer = renderer;
        this.properties = properties;
        this.clock = clock;
        this.messageIdSupplier = messageIdSupplier;
    }

    @Override
    public SendResult deliver(ReachMessage message) {
        Instant now = clock.instant();
        RenderedEmail rendered = renderer.render(message, now);

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(properties.recipient());
        mail.setSubject(rendered.subject());
        mail.setText(rendered.body());

        // A transient transport failure (MailException) is intentionally NOT caught here: it propagates
        // so EmailAdapter translates it into a retryable provider failure and the breaker counts it.
        mailSender.send(mail);

        // SimpleMailMessage send() returns no provider id, so emit a locally generated one.
        String providerMessageId = messageIdSupplier.get();

        // Conservative operational log: identifiers only. The recipient/from address, subject, and body
        // are deliberately excluded to keep recipient PII out of logs (spec「Provider logs remain
        // conservative」). userId/templateRef are non-PII routing identifiers.
        LOG.debug(
                "Local SMTP email delivered: providerMessageId={} userId={} templateRef={}",
                providerMessageId,
                message.userId(),
                message.templateRef());

        return new SendResult(providerMessageId);
    }
}
