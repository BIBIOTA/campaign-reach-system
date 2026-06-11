/**
 * Reach module — dispatcher layer (outbound message dispatch).
 *
 * <p>Owns the two-phase transactional {@code ReachTask} dispatch (task 9.1): {@link
 * com.example.campaignreach.reach.dispatcher.ReachTaskDispatcher} polls, claims a batch via {@link
 * com.example.campaignreach.reach.dispatcher.ReachTaskDispatchDao} under {@code FOR UPDATE SKIP
 * LOCKED} + lease (stage one), runs the external send through the matching {@link
 * com.example.campaignreach.reach.channel.ChannelAdapter} outside any transaction, then writes the
 * outcome back in a fresh short transaction (stage two) — SENT, RETRY_SCHEDULED with exponential
 * backoff ({@link com.example.campaignreach.reach.dispatcher.RetryBackoffSchedule}), or FAILED.
 */
package com.example.campaignreach.reach.dispatcher;
