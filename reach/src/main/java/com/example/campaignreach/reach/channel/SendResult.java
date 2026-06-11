package com.example.campaignreach.reach.channel;

/**
 * The synchronous outcome of a {@link ChannelAdapter#send(ReachMessage)} call (class-model
 * {@code SendResult}; spec §4, FR-009).
 *
 * <p>A successful send returns a {@code SendResult} carrying the provider's message id. A failure
 * does <strong>not</strong> return a result — the adapter throws (see {@link RetryableSendException}
 * for failures the dispatcher should retry), so the caller never has to inspect a status flag to
 * detect failure.
 *
 * @param providerMessageId the id assigned by the external provider for the accepted message
 */
public record SendResult(String providerMessageId) {

    /** Validates the result invariant: a provider message id must be present on success. */
    public SendResult {
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new IllegalArgumentException("providerMessageId must not be blank");
        }
    }
}
