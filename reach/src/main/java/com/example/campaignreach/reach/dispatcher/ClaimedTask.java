package com.example.campaignreach.reach.dispatcher;

import com.example.campaignreach.shared.event.Channel;
import java.util.UUID;

/**
 * A {@code reach_task} row claimed by the dispatcher's stage-one transaction (task 9.1): the minimal
 * fields the out-of-transaction external send needs, read while the row was marked PROCESSING under
 * {@code FOR UPDATE SKIP LOCKED}.
 *
 * <p>PII-minimized by design (NFR-005): a claimed task carries only the {@code userId} plus what the
 * {@link com.example.campaignreach.reach.channel.ChannelAdapter} needs to route and render — it never
 * carries the resolved email address. {@code templateRef} comes from the frozen {@code
 * reach_plan_snapshot} of the owning {@code reach_request}.
 *
 * @param taskId the {@code reach_task} primary key (the stage-two write-back targets this id)
 * @param campaignId the owning campaign (carried for the dead-letter event / partition key, task 9.2)
 * @param userId the upstream member identity to reach
 * @param channel the delivery channel chosen at fan-out time (the dispatcher selects the matching
 *     adapter)
 * @param sendCycleKey the send-cycle key of the originating request (dead-letter replay context +
 *     partition key composite, task 9.2)
 * @param templateRef the content template reference from the frozen reach plan
 * @param retryCount the number of retries already scheduled before this attempt ({@code 0} for a
 *     task that has not yet failed) — drives the backoff schedule and the bounded-attempts terminal
 *     condition
 */
public record ClaimedTask(
        UUID taskId,
        UUID campaignId,
        UUID userId,
        Channel channel,
        String sendCycleKey,
        String templateRef,
        int retryCount) {

    /** Validates the invariants: identity, channel, send cycle, and template must be present. */
    public ClaimedTask {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId must not be null");
        }
        if (campaignId == null) {
            throw new IllegalArgumentException("campaignId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }
        if (sendCycleKey == null || sendCycleKey.isBlank()) {
            throw new IllegalArgumentException("sendCycleKey must not be blank");
        }
        if (templateRef == null || templateRef.isBlank()) {
            throw new IllegalArgumentException("templateRef must not be blank");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
    }
}
