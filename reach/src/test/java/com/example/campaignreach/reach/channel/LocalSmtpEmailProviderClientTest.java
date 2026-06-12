package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.campaignreach.shared.event.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Fast unit tests for {@link LocalSmtpEmailProviderClient} (task 2.2; spec requirement「本機 SMTP Email
 * provider shall deliver EMAIL reach tasks to Mailpit」). {@link JavaMailSender} is mocked, so no SMTP
 * server or Docker is needed.
 *
 * <p>Test names map to the spec scenarios for traceability.
 */
class LocalSmtpEmailProviderClientTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final Instant SENT_AT = Instant.parse("2026-06-12T09:30:00Z");
    private static final String FROM = "local-campaign@example.test";
    private static final String RECIPIENT = "mailpit-sink@example.test";
    private static final String TEMPLATE_REF = "summer-sale-email";

    private final Clock fixedClock = Clock.fixed(SENT_AT, ZoneOffset.UTC);
    private final LocalEmailTemplateRenderer renderer = new LocalEmailTemplateRenderer();
    private final LocalSmtpEmailProperties properties =
            new LocalSmtpEmailProperties("localhost", 1025, FROM, RECIPIENT, Duration.ofSeconds(5));

    @Test
    @DisplayName("EMAIL reach task is sent to Mailpit")
    void emailReachTaskIsSentToMailpit() {
        JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
        LocalSmtpEmailProviderClient client = new LocalSmtpEmailProviderClient(
                mailSender, renderer, properties, fixedClock, () -> "local-smtp-fixed");
        ReachMessage message = new ReachMessage(USER_ID, Channel.EMAIL, TEMPLATE_REF);

        SendResult result = client.deliver(message);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();

        // Sent from the configured from-address to the fixed local recipient.
        assertThat(sent.getFrom()).isEqualTo(FROM);
        assertThat(sent.getTo()).containsExactly(RECIPIENT);

        // Subject and body come from the renderer for the fixed send time.
        RenderedEmail expected = renderer.render(message, SENT_AT);
        assertThat(sent.getSubject()).isEqualTo(expected.subject());
        assertThat(sent.getSubject()).contains("[Local Campaign Reach]").contains(TEMPLATE_REF);
        assertThat(sent.getText()).isEqualTo(expected.body());
        assertThat(sent.getText()).contains(USER_ID.toString()).contains(TEMPLATE_REF);

        // A non-blank provider message id is returned.
        assertThat(result.providerMessageId()).isNotBlank().isEqualTo("local-smtp-fixed");
    }

    @Test
    @DisplayName("SMTP transport failure follows retryable provider path")
    void smtpTransportFailureFollowsRetryableProviderPath() {
        JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
        LocalSmtpEmailProviderClient client = new LocalSmtpEmailProviderClient(
                mailSender, renderer, properties, fixedClock, () -> "local-smtp-fixed");
        ReachMessage message = new ReachMessage(USER_ID, Channel.EMAIL, TEMPLATE_REF);

        // Mailpit unavailable / SMTP transport error: Spring surfaces it as a MailException.
        doThrow(new MailSendException("simulated SMTP transport failure"))
                .when(mailSender)
                .send(Mockito.any(SimpleMailMessage.class));

        // The failure propagates as a RuntimeException that is NOT NonRetryableSendException, so
        // EmailAdapter translates it into a retryable provider failure (and the breaker counts it).
        assertThatThrownBy(() -> client.deliver(message))
                .isInstanceOf(MailException.class)
                .isNotInstanceOf(NonRetryableSendException.class);
    }

    @Test
    @DisplayName("SMTP transport failure follows retryable provider path (through real EmailAdapter)")
    void localProviderTransientFailureSurfacesAsRetryableThroughEmailAdapter() {
        // Wire the REAL local provider (only the JavaMailSender is mocked) into a REAL EmailAdapter with a
        // fresh CLOSED breaker, proving the transient SMTP failure travels the existing retryable provider
        // path end-to-end for the local provider — not just generically (EmailAdapterCircuitBreakerTest).
        JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
        LocalSmtpEmailProviderClient provider = new LocalSmtpEmailProviderClient(
                mailSender, renderer, properties, fixedClock, () -> "local-smtp-fixed");
        doThrow(new MailSendException("simulated SMTP transport failure"))
                .when(mailSender)
                .send(Mockito.any(SimpleMailMessage.class));

        EmailAdapter adapter = new EmailAdapter(provider, freshClosedBreaker());
        ReachMessage message = new ReachMessage(USER_ID, Channel.EMAIL, TEMPLATE_REF);

        // The MailException raised by the local provider is translated into a retryable providerFailure
        // (NOT a breaker short-circuit), exactly as the existing EmailAdapter retryable path prescribes.
        assertThatThrownBy(() -> adapter.send(message))
                .isInstanceOf(RetryableSendException.class)
                .matches(ex -> !((RetryableSendException) ex).isBreakerOpen(), "providerFailure (breaker not open)");
    }

    /** A fresh, CLOSED real circuit breaker mirroring EmailAdapterCircuitBreakerTest's construction. */
    private static CircuitBreaker freshClosedBreaker() {
        EmailChannelProperties props = new EmailChannelProperties(
                new EmailChannelProperties.CircuitBreaker(8, 8, 50f, Duration.ofSeconds(30), 2));
        return CircuitBreakerRegistry.ofDefaults()
                .circuitBreaker("local-retryable-path", props.toCircuitBreakerConfig());
    }

    @Test
    @DisplayName("Fixed local recipient is not persisted")
    void fixedLocalRecipientIsNotPersisted() {
        // A recognizable recipient sourced ONLY from local provider configuration.
        String fixedRecipient = "local-inbox@example.test";
        LocalSmtpEmailProperties configWithFixedRecipient =
                new LocalSmtpEmailProperties("localhost", 1025, FROM, fixedRecipient, Duration.ofSeconds(5));

        JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
        LocalSmtpEmailProviderClient client = new LocalSmtpEmailProviderClient(
                mailSender, renderer, configWithFixedRecipient, fixedClock, () -> "local-smtp-fixed");
        ReachMessage message = new ReachMessage(USER_ID, Channel.EMAIL, TEMPLATE_REF);

        client.deliver(message);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();

        // The recipient is read ONLY from local provider configuration, not from the ReachMessage.
        assertThat(sent.getTo()).containsExactly(fixedRecipient);

        // The rendered body must NOT embed the recipient address (no recipient PII in content).
        assertThat(sent.getText()).doesNotContain(fixedRecipient);

        // Structural proof that ReachMessage carries no email/recipient field: its record components are
        // exactly (userId, channel, templateRef) — there is no accessor that could carry an email address.
        List<String> componentNames = Arrays.stream(ReachMessage.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(componentNames).containsExactly("userId", "channel", "templateRef");
        assertThat(componentNames)
                .noneMatch(name -> name.toLowerCase().contains("email")
                        || name.toLowerCase().contains("recipient")
                        || name.toLowerCase().contains("address"));
    }

    @Test
    @DisplayName("Provider logs remain conservative")
    void providerLogsRemainConservative() {
        String fixedRecipient = "local-inbox@example.test";
        LocalSmtpEmailProperties configWithFixedRecipient =
                new LocalSmtpEmailProperties("localhost", 1025, FROM, fixedRecipient, Duration.ofSeconds(5));

        JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
        LocalSmtpEmailProviderClient client = new LocalSmtpEmailProviderClient(
                mailSender, renderer, configWithFixedRecipient, fixedClock, () -> "local-smtp-fixed");
        ReachMessage message = new ReachMessage(USER_ID, Channel.EMAIL, TEMPLATE_REF);

        // Capture log events emitted by the provider client logger at DEBUG (the per-send log level).
        ch.qos.logback.classic.Logger clientLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LocalSmtpEmailProviderClient.class);
        Level previousLevel = clientLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        clientLogger.addAppender(appender);
        clientLogger.setLevel(Level.DEBUG);
        try {
            client.deliver(message);
        } finally {
            clientLogger.detachAppender(appender);
            clientLogger.setLevel(previousLevel);
        }

        String logText =
                appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);

        // The conservative log includes the provider message id and the userId identifier.
        assertThat(logText).contains("local-smtp-fixed").contains(USER_ID.toString());

        // It must NOT leak any recipient PII or email content: recipient/from address, subject, body notice.
        assertThat(logText).doesNotContain(fixedRecipient);
        assertThat(logText).doesNotContain(FROM);
        assertThat(logText).doesNotContain("[Local Campaign Reach]");
        assertThat(logText).doesNotContain("This is a LOCAL TEST email from campaign-reach-system");
    }
}
