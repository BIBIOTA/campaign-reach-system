package com.example.campaignreach.shared.event;

import java.util.UUID;

/**
 * User-level reach task — emitted by the reach orchestrator after it expands a {@link
 * ReachRequested} into N per-recipient/per-channel tasks (design.md §5). One event per created
 * {@code reach_task} row.
 *
 * <p>The {@code sendCycle} value is the same string carried by the originating {@link
 * ReachRequested} and persisted as {@code send_cycle_key} — only the naming style differs.
 *
 * @param reachTaskId the created task id (ER {@code reach_task.id})
 * @param reachRequestId owning activity-level batch (ER {@code reach_task.reach_request_id})
 * @param campaignId owning campaign (ER {@code reach_task.campaign_id})
 * @param userId recipient user (ER {@code reach_task.user_id})
 * @param sendCycle send-cycle key; same value as the persisted {@code send_cycle_key}
 * @param channel delivery channel (ER {@code reach_task.channel})
 */
public record ReachTaskCreated(
        UUID reachTaskId, UUID reachRequestId, UUID campaignId, UUID userId, String sendCycle, Channel channel) {}
