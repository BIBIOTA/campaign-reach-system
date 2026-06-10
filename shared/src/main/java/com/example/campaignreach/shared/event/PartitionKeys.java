package com.example.campaignreach.shared.event;

import java.util.UUID;

/**
 * Deterministic Kafka partition-key derivation per topic (design.md §9). A Kafka message key
 * determines its partition; these helpers are <strong>pure and reproducible</strong> (same inputs
 * always yield the same key) so partition placement — and therefore the idempotency that depends on
 * ordering and re-delivery — stays stable across producers and retries.
 *
 * <ul>
 *   <li>{@code domain.events} → keyed by {@code user_id}: same-user behavior events stay ordered and
 *       spread naturally, avoiding a hot partition.
 *   <li>{@code reach.requested} → keyed by {@code reach_request_id} when known, else a deterministic
 *       composite of {@code campaign_id + send_cycle_key}. <strong>Never bare {@code campaign_id}</strong>:
 *       that would funnel one large campaign's requests onto a single partition and starve other
 *       campaigns (violating NFR-002 "campaigns do not interfere with each other").
 *   <li>{@code reach.dlq} → reuses the source task's key so replay preserves the original ordering.
 * </ul>
 *
 * <p>This is a constants/utility holder: a {@code final} class with a private constructor, not a
 * Spring bean.
 */
public final class PartitionKeys {

    private PartitionKeys() {
        throw new AssertionError("No instances of utility holder PartitionKeys");
    }

    /**
     * Partition key for a {@code domain.events} message: the user id. Guarantees per-user ordering
     * and natural spread.
     *
     * @param userId the behavior event's subject user (non-null)
     * @return the deterministic partition key
     */
    public static String forDomainEvent(UUID userId) {
        return requireNonNull(userId, "userId").toString();
    }

    /**
     * Partition key for a {@code reach.requested} message when the {@code reach_request_id} is already
     * assigned. Preferred over the composite form.
     *
     * @param reachRequestId the activity-level request id (non-null)
     * @return the deterministic partition key
     */
    public static String forReachRequested(UUID reachRequestId) {
        return requireNonNull(reachRequestId, "reachRequestId").toString();
    }

    /**
     * Partition key for a {@code reach.requested} message before a {@code reach_request_id} exists:
     * the deterministic composite {@code campaignId + ":" + sendCycle}. This is intentionally NOT bare
     * {@code campaignId} (avoids the hot partition described in design.md §9 / NFR-002).
     *
     * @param campaignId owning campaign (non-null)
     * @param sendCycle send-cycle key, same value as the persisted {@code send_cycle_key} (non-null)
     * @return the deterministic partition key
     */
    public static String forReachRequested(UUID campaignId, String sendCycle) {
        return requireNonNull(campaignId, "campaignId") + ":" + requireNonBlank(sendCycle, "sendCycle");
    }

    /**
     * Partition key for a {@link ReachRequested} event, picking the best available basis: the
     * activity-level composite ({@code campaignId + sendCycle}) since the request id is assigned at
     * persistence time. Deterministic for a given event.
     *
     * @param event the reach request (non-null)
     * @return the deterministic partition key
     */
    public static String forReachRequested(ReachRequested event) {
        requireNonNull(event, "event");
        return forReachRequested(event.campaignId(), event.sendCycle());
    }

    /**
     * Partition key for a {@code reach.dlq} message: reuse the source task's key so replay keeps the
     * original ordering. The source key is the dead-lettered task's existing partition key.
     *
     * @param sourceTaskKey the partition key of the originating task (non-null)
     * @return the same key, unchanged
     */
    public static String forReachDlq(String sourceTaskKey) {
        return requireNonBlank(sourceTaskKey, "sourceTaskKey");
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    private static String requireNonBlank(String value, String name) {
        if (requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
