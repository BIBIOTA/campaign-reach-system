package com.example.campaignreach.shared.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reach task dead-letter event — emitted by the reach dispatcher when a {@code reach_task} exhausts
 * its bounded retries (task 9.2, spec「失敗保留與 DLQ」, §6 FR-016/US-006). Published to
 * {@link KafkaTopics#REACH_DLQ} so the dead-lettered task is durably retained for manual inspection
 * and replay rather than being silently lost.
 *
 * <p>This is a cross-module <strong>wire contract</strong> and therefore lives in the shared kernel
 * alongside {@link KafkaTopics#REACH_DLQ} and {@link PartitionKeys#forReachDlq(String)}: the producer
 * is reach, but any future replay / inspection tooling (a separate consumer, an ops console) reads the
 * same schema, so it must stay a stable shared contract, not a reach-internal type.
 *
 * <p><strong>PII-minimized (NFR-005, spec §10).</strong> The payload carries only the {@code userId}
 * member identity and routing/diagnostic metadata — never the resolved email address or rendered
 * content. That is enough to locate the originating task and replay it without leaking recipient PII.
 *
 * @param reachTaskId the dead-lettered {@code reach_task} id (the row marked {@code DLQ})
 * @param campaignId the owning campaign (for ops grouping / replay scoping)
 * @param userId the member identity the task targeted (PII-minimized identity, no address)
 * @param channel the delivery channel the task used
 * @param sendCycleKey the send-cycle key of the originating request (replay idempotency context)
 * @param reason a short last-error description explaining why retries were exhausted
 * @param attempts the number of retries that were attempted before exhaustion
 * @param occurredAt when the task was dead-lettered
 */
public record ReachTaskDeadLettered(
        UUID reachTaskId,
        UUID campaignId,
        UUID userId,
        Channel channel,
        String sendCycleKey,
        String reason,
        int attempts,
        Instant occurredAt) {

    /** Validates required fields at construction time so an invalid event fails before serialization. */
    public ReachTaskDeadLettered {
        Objects.requireNonNull(reachTaskId, "reachTaskId must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
        if (sendCycleKey == null || sendCycleKey.isBlank()) {
            throw new IllegalArgumentException("sendCycleKey must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
