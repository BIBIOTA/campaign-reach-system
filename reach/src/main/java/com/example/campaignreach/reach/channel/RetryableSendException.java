package com.example.campaignreach.reach.channel;

/**
 * Signals a {@link ChannelAdapter#send(ReachMessage)} failure that the dispatcher SHOULD retry
 * (spec §4 FR-009; §6 NFR-004).
 *
 * <p>This is the contract seam between the channel-adapter layer (task 8.1) and the dispatcher
 * (Section 9). Two distinct conditions both surface as this single retryable signal so the
 * dispatcher does not need to know channel internals:
 *
 * <ul>
 *   <li><strong>Breaker open / fast-fail</strong> — the circuit breaker around the adapter is OPEN
 *       (or trips a probe), so the call short-circuits without ever touching the provider. The
 *       dispatcher treats this exactly like a transient failure: scenario「已 PROCESSING 後 breaker
 *       失敗」→ stage-two write-back to {@code RETRY_SCHEDULED}, never stuck in {@code PROCESSING}.
 *   <li><strong>Transient provider failure</strong> — the external Email provider responded with a
 *       retryable error (timeout, 5xx, throttling).
 * </ul>
 *
 * <p>To let a dispatcher implement scenario「breaker 開啟時不卡任務」(skip before marking
 * {@code PROCESSING}, leave the task {@code PENDING}), {@link #isBreakerOpen()} distinguishes a
 * pure breaker short-circuit from a real provider failure. The dispatcher-side wiring that consults
 * this lands in Section 9; this exception only exposes the seam.
 */
public class RetryableSendException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean breakerOpen;

    private RetryableSendException(String message, Throwable cause, boolean breakerOpen) {
        super(message, cause);
        this.breakerOpen = breakerOpen;
    }

    /**
     * A retryable failure originating from the external provider call itself.
     *
     * @param message human-readable cause description
     * @param cause the underlying provider/transport failure
     * @return a retryable exception whose {@link #isBreakerOpen()} is {@code false}
     */
    public static RetryableSendException providerFailure(String message, Throwable cause) {
        return new RetryableSendException(message, cause, false);
    }

    /**
     * A retryable failure caused purely by the circuit breaker short-circuiting the call (OPEN, or a
     * rejected half-open probe) — the provider was never contacted.
     *
     * @param message human-readable cause description
     * @param cause the breaker's {@code CallNotPermittedException}
     * @return a retryable exception whose {@link #isBreakerOpen()} is {@code true}
     */
    public static RetryableSendException breakerOpen(String message, Throwable cause) {
        return new RetryableSendException(message, cause, true);
    }

    /**
     * @return {@code true} if this failure is a pure breaker short-circuit (the provider was not
     *     contacted), which lets a dispatcher skip the task before marking {@code PROCESSING};
     *     {@code false} if the provider call itself failed.
     */
    public boolean isBreakerOpen() {
        return breakerOpen;
    }
}
