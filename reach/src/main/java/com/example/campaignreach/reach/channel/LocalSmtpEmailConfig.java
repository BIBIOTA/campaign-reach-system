package com.example.campaignreach.reach.channel;

import java.time.Clock;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Local-only wiring that registers the SMTP {@link EmailProviderClient} which delivers EMAIL reach
 * tasks to Mailpit during local smoke tests (design.md §架構; spec requirement「本機 SMTP Email provider
 * shall deliver EMAIL reach tasks to Mailpit」).
 *
 * <p><strong>Why a component-scanned {@code @Configuration} and not an {@code AutoConfiguration}.</strong>
 * The provider-gated {@link EmailAdapter} is registered by {@link EmailAdapterAutoConfiguration} via
 * {@code @Bean @ConditionalOnBean(EmailProviderClient.class)}, and {@code @ConditionalOnBean} can only
 * see bean definitions registered <em>before</em> it runs. Component-scanned {@code @Configuration}
 * beans are registered during the user-config phase — <em>before</em> the auto-configuration phase — so
 * by the time {@link EmailAdapterAutoConfiguration}'s {@code @ConditionalOnBean} is evaluated, this
 * {@link EmailProviderClient} bean is already present and {@link EmailAdapter} activates
 * deterministically. Defining the local provider in an {@code AutoConfiguration} instead would make the
 * two conditions race within the same (auto-config) phase, so it is deliberately a regular
 * {@code @Configuration} here. For the same reason this class uses no {@code @ConditionalOnMissingBean}
 * and does not touch {@link EmailAdapterAutoConfiguration} / {@link EmailChannelConfig}.
 *
 * <p><strong>Gating.</strong> The whole configuration is double-gated by {@code @Profile("local")} and
 * {@code @ConditionalOnProperty(campaignreach.email-provider.mode=smtp-local)}: it contributes nothing
 * unless the application is explicitly started in local SMTP mode, so no non-local environment can ever
 * enable local mail delivery.
 *
 * <p><strong>Spring Mail.</strong> The {@link JavaMailSender} is built explicitly from
 * {@link LocalSmtpEmailProperties} rather than relying on Spring Boot's {@code MailSenderAutoConfiguration}
 * ({@code spring.mail.*}), so the mail sender is fully scoped behind the same local conditions and never
 * leaks into other profiles. Mailpit needs no auth, so no username/password is set.
 */
@Configuration
@Profile("local")
@ConditionalOnProperty(name = "campaignreach.email-provider.mode", havingValue = "smtp-local")
@EnableConfigurationProperties(LocalSmtpEmailProperties.class)
public class LocalSmtpEmailConfig {

    /**
     * Builds the Spring Mail sender pointed at the local SMTP sink (Mailpit), applying the configured
     * timeout to the JavaMail connect/read/write timeouts.
     *
     * @param properties the validated local SMTP settings (host, port, timeout)
     * @return a {@link JavaMailSender} bound to the local SMTP host/port with no auth
     */
    @Bean
    public JavaMailSender localSmtpMailSender(LocalSmtpEmailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());

        long timeoutMillis = properties.timeout().toMillis();
        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.smtp.connectiontimeout", Long.toString(timeoutMillis));
        javaMailProperties.put("mail.smtp.timeout", Long.toString(timeoutMillis));
        javaMailProperties.put("mail.smtp.writetimeout", Long.toString(timeoutMillis));
        return sender;
    }

    /**
     * The clock the local provider stamps render times with. Kept local-profile-scoped here (rather than
     * a global bean) so it cannot accidentally become an application-wide {@code Clock} dependency.
     *
     * @return a UTC system clock
     */
    @Bean
    public Clock localSmtpClock() {
        return Clock.systemUTC();
    }

    /**
     * Registers the local SMTP provider as an {@link EmailProviderClient} (the interface type, so
     * {@link EmailAdapterAutoConfiguration}'s {@code @ConditionalOnBean(EmailProviderClient.class)}
     * matches and {@link EmailAdapter} activates).
     *
     * @param mailSender the local SMTP mail sender
     * @param properties the local SMTP settings (from/recipient)
     * @param clock the render-time clock
     * @return the local SMTP {@link EmailProviderClient}
     */
    @Bean
    public EmailProviderClient localSmtpEmailProviderClient(
            JavaMailSender mailSender, LocalSmtpEmailProperties properties, Clock clock) {
        return new LocalSmtpEmailProviderClient(mailSender, new LocalEmailTemplateRenderer(), properties, clock);
    }
}
