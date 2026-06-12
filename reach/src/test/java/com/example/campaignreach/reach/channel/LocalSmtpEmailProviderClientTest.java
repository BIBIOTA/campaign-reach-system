package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.campaignreach.shared.event.Channel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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
}
