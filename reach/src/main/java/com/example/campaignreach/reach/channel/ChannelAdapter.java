package com.example.campaignreach.reach.channel;

import com.example.campaignreach.shared.event.Channel;

/**
 * Strategy for delivering a {@link ReachMessage} over one concrete channel (class-model
 * {@code ChannelAdapter} {@literal <<Adapter>>}; spec §4, FR-009).
 *
 * <p>This is the Open/Closed extension point for the reach module: adding a new channel (SMS, PUSH,
 * …) means adding a new {@code ChannelAdapter} implementation and registering it as a Spring bean —
 * the dispatcher (Section 9) selects the adapter whose {@link #channel()} matches the
 * {@code reachPlan} and never has to change. {@link EmailAdapter} is the first implementation.
 *
 * <p>Contract: {@link #send(ReachMessage)} is <strong>synchronous</strong>. It returns a
 * {@link SendResult} on success; on a retryable failure it throws {@link RetryableSendException}
 * (rather than returning a status flag) so the dispatcher's retry path is driven by exceptions.
 */
public interface ChannelAdapter {

    /**
     * @return the channel this adapter delivers over; the dispatcher matches this against the
     *     {@code reachPlan} channel to route a message.
     */
    Channel channel();

    /**
     * Delivers the message synchronously over {@link #channel()}.
     *
     * @param message the PII-minimized message to deliver
     * @return the provider's accepted-send result
     * @throws RetryableSendException if delivery fails in a way the dispatcher should retry
     *     (transient provider failure, or the circuit breaker short-circuiting the call)
     */
    SendResult send(ReachMessage message);

    /**
     * Whether a send over this channel is worth attempting right now — the dispatcher's pre-claim
     * breaker check (scenario「breaker 開啟時不卡任務」). When this returns {@code false} the dispatcher
     * skips claiming tasks for this channel and leaves them PENDING for a later re-scan, rather than
     * marking them PROCESSING only to fast-fail.
     *
     * <p>The default is {@code true}: an adapter with no degradation gating is always available.
     * {@link EmailAdapter} overrides this to report its circuit-breaker state (non-consuming — it does
     * not acquire a breaker permit, so it never perturbs the sliding-window accounting).
     *
     * @return {@code true} if a send should be attempted; {@code false} while the channel is degraded
     *     (e.g. the breaker is OPEN)
     */
    default boolean isAvailable() {
        return true;
    }
}
