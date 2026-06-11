package com.example.campaignreach.reach.metrics;

import com.example.campaignreach.reach.orchestrator.ReachTaskStatus;
import com.example.campaignreach.shared.event.Channel;
import java.util.UUID;

/**
 * One recipient's reach task within a campaign (task 10.3, FR-018, US-007, NFR-005): a single entry
 * of the「單筆收件人狀態」response.
 *
 * <p>A user may have several tasks for one campaign (different send cycles and/or channels — the
 * {@code reach_task} unique key is {@code (campaign_id, user_id, send_cycle_key, channel)}), so the
 * per-recipient lookup returns the <em>set</em> of these entries (see {@link RecipientReachView}).
 *
 * <p><strong>PII minimization (NFR-005):</strong> this carries only identifiers and status —
 * campaign id, user id, channel, send-cycle key, and the lifecycle status. It deliberately never
 * exposes an email address or any message content; {@code reach_task} stores only {@code user_id} by
 * design, so no PII is available to leak here.
 *
 * @param campaignId owning campaign
 * @param userId recipient user
 * @param channel delivery channel of this task
 * @param sendCycleKey the send-cycle key this task belongs to
 * @param status the task's current lifecycle status (待發送 / 已送達 / 失敗 等)
 */
public record RecipientReachStatus(
        UUID campaignId, UUID userId, Channel channel, String sendCycleKey, ReachTaskStatus status) {}
