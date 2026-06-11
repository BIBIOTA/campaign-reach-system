/**
 * Reach module — channel adapter layer (spec §4 FR-009; §6 NFR-004).
 *
 * <p>Defines the {@link com.example.campaignreach.reach.channel.ChannelAdapter} strategy — the
 * Open/Closed extension point the dispatcher (Section 9) uses to route a {@link
 * com.example.campaignreach.reach.channel.ReachMessage} to the adapter whose channel matches the
 * {@code reachPlan}. {@link com.example.campaignreach.reach.channel.EmailAdapter} is the first
 * implementation; it wraps the external Email provider in a Resilience4j circuit breaker so an
 * unavailable provider degrades stably (open on a configurable failure-rate threshold, cool-down,
 * half-open probing) without cascading. Retryable failures — including breaker short-circuits —
 * surface as {@link com.example.campaignreach.reach.channel.RetryableSendException}, the seam the
 * dispatcher consumes for its RETRY_SCHEDULED write-back.
 */
package com.example.campaignreach.reach.channel;
