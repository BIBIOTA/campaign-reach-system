package com.example.campaignreach.shared.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Send result — emitted by the reach dispatcher after an attempt against the provider completes
 * (design.md §5). Corresponds to a {@code send_result} row.
 *
 * @param reachTaskId task this result belongs to (ER {@code send_result.reach_task_id})
 * @param providerMessageId provider's message id, used for dedupe (ER {@code provider_message_id});
 *     may be {@code null} when the provider returned none (e.g. an early failure)
 * @param outcome delivery outcome (ER {@code send_result.outcome})
 * @param occurredAt when the result occurred (ER {@code send_result.occurred_at})
 */
public record SendResultRecorded(UUID reachTaskId, String providerMessageId, String outcome, Instant occurredAt) {}
