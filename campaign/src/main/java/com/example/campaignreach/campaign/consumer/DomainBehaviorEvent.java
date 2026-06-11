package com.example.campaignreach.campaign.consumer;

import java.util.Objects;

/**
 * Minimal inbound contract for a user behavior event arriving on {@code domain.events} (path 2,
 * task 6.3, FR-008, US-005; {@code diagrams/01-sequence-reach-flow.puml}).
 *
 * <p>This is an <strong>external → campaign</strong> message, not a campaign ↔ reach contract, so it
 * deliberately lives in the campaign consumer package and <em>not</em> in the shared kernel (which
 * holds only cross-module stable contracts). It is intentionally minimal (YAGNI): only the fields the
 * trigger-matching and {@code send_cycle_key} derivation actually read are carried.
 *
 * <ul>
 *   <li>{@code eventId} — the source event's unique id; becomes the {@code triggerEventId} and the
 *       {@code event:{triggerEventId}} send-cycle key on the emitted {@code ReachRequested} (§5).
 *   <li>{@code eventType} — the behavior event name (e.g. {@code CART_ABANDONED}); matched against
 *       RUNNING campaigns via {@code TriggerContext.event(campaignType, eventType)}.
 *   <li>{@code userId} — the subject user; carried so the contract is meaningful, though reach (not
 *       campaign) expands the audience — this stays an activity-level trigger with no recipient list.
 * </ul>
 *
 * @param eventId the source behavior event's unique id; must not be blank
 * @param eventType the behavior event name; must not be blank
 * @param userId the subject user id; must not be blank
 */
public record DomainBehaviorEvent(String eventId, String eventType, String userId) {

    /** Validates required fields at construction so malformed events fail before matching. */
    public DomainBehaviorEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        Objects.requireNonNull(userId, "userId must not be null");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }
}
