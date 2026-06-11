package com.example.campaignreach.reach.channel;

/**
 * The thin abstraction over the external Email provider SDK (SendGrid / SES) that {@link EmailAdapter}
 * wraps (spec §4 FR-009: "EmailAdapter 介接 SendGrid/SES 並包一層"; component diagram: EmailAdapter
 * → [Email Provider (SendGrid/SES)]).
 *
 * <p>Keeping the raw provider call behind this seam means the circuit breaker, channel routing and
 * PII boundary all live in {@link EmailAdapter}, while a concrete provider binding (the actual
 * SendGrid/SES HTTP client) is a single swappable implementation. The concrete binding and its
 * credential wiring ({@code EMAIL_PROVIDER_API_KEY}) are intentionally out of scope for task 8.1.
 */
public interface EmailProviderClient {

    /**
     * Hands the message to the external provider and returns its accepted-send result.
     *
     * @param message the message to deliver
     * @return the provider's accepted-send result
     * @throws RuntimeException if the provider call fails (timeout, transport error, 5xx); the
     *     {@link EmailAdapter} translates such failures into {@link RetryableSendException} and the
     *     circuit breaker counts them toward its failure rate.
     */
    SendResult deliver(ReachMessage message);
}
