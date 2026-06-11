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
}
