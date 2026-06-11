package com.example.campaignreach.reach.channel;

import com.example.campaignreach.shared.event.Channel;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Pre-send suppression filter (design.md §10「退訂與抑制名單」; FR-015, NFR-005).
 *
 * <p>Before a reach task is sent, the send path asks this guard whether the recipient is suppressed on
 * the task's channel. A hit (退訂 / 硬退信 / 投訴) yields a {@link SuppressionVerdict#suppressed(
 * SuppressionReason)} verdict — the task must be marked {@link
 * com.example.campaignreach.reach.orchestrator.ReachTaskStatus#FAILED} (a non-retryable reason) and
 * NOT sent. A miss yields {@link SuppressionVerdict#notSuppressed()} so the send proceeds. The check is
 * channel-scoped: a suppression on one channel does not suppress a send on another for the same user.
 *
 * <p><strong>Dispatcher seam.</strong> This guard only computes the decision; it does not mutate any
 * {@code reach_task} row. Applying the verdict — transitioning the task to FAILED and skipping the
 * send — is wired into the Section-9 dispatcher (tasks 9.x), which owns the two-phase claim and
 * status-update SQL. This mirrors how task 8.1 left the {@code RetryableSendException} / breaker-state
 * seam for the dispatcher to consume.
 */
@Component
public class SuppressionGuard {

    private final SuppressionRepository suppressionRepository;

    public SuppressionGuard(SuppressionRepository suppressionRepository) {
        this.suppressionRepository = suppressionRepository;
    }

    /**
     * Decides whether a recipient may be sent to on the given channel.
     *
     * @param userId the recipient's upstream member id (the only PII-minimized identity we hold)
     * @param channel the channel the send would use
     * @return a suppressed verdict (carrying the non-retryable reason) on a hit, otherwise a
     *     not-suppressed verdict
     */
    public SuppressionVerdict evaluate(UUID userId, Channel channel) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }
        return suppressionRepository
                .findByUserIdAndChannel(userId, channel)
                .map(entry -> SuppressionVerdict.suppressed(entry.getReason()))
                .orElseGet(SuppressionVerdict::notSuppressed);
    }
}
