package com.example.campaignreach.reach.channel;

/**
 * Signals a {@link ChannelAdapter#send(ReachMessage)} failure that the dispatcher MUST NOT retry —
 * a <strong>permanent</strong> provider-side failure (task 9.2, spec「可靠發送與重試」, §6 FR-015).
 *
 * <p>This is the non-retryable sibling of {@link RetryableSendException}. Where a retryable failure
 * (transient provider error / breaker short-circuit) drives the task to {@code RETRY_SCHEDULED} with
 * exponential backoff, a non-retryable failure drives it straight to {@code FAILED} without burning a
 * retry — realizing the state-diagram edge {@code PROCESSING --> FAILED : 不可重試(無效地址/退訂)}.
 *
 * <p><strong>Taxonomy at the provider seam.</strong> {@link EmailProviderClient#deliver} may throw
 * this exception to mark a provider-call-time permanent failure (e.g. the provider rejected the
 * recipient address as invalid). {@link EmailAdapter} lets it propagate <em>without</em> letting the
 * circuit breaker count it: a bad address is a per-recipient data problem, not a provider-health
 * signal, so counting it would wrongly trip the breaker for healthy traffic. A genuinely transient
 * {@code RuntimeException} from the provider still becomes {@link RetryableSendException#providerFailure}
 * and is counted by the breaker as before.
 *
 * <p><strong>Not the suppression path.</strong> Unsubscribe / hard-bounce / complaint suppression is
 * handled pre-send by {@code SuppressionGuard} (→ FAILED before any provider call); this exception is
 * specifically for permanent failures surfaced by the provider call itself.
 */
public class NonRetryableSendException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * A permanent provider-side failure that must not be retried.
     *
     * @param message human-readable cause description (stored as the task's {@code last_error})
     */
    public NonRetryableSendException(String message) {
        super(message);
    }

    /**
     * A permanent provider-side failure that must not be retried, carrying the underlying cause.
     *
     * @param message human-readable cause description
     * @param cause the underlying provider failure that is known to be permanent
     */
    public NonRetryableSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
