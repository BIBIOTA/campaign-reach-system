package com.example.campaignreach.shared.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * In-process cross-module contract emitted after a campaign lifecycle transition is persisted. The
 * reach module listens for terminal/deactivated statuses without importing campaign domain classes.
 *
 * @param campaignId owning campaign
 * @param previousStatus status before the transition
 * @param newStatus status after the transition
 * @param occurredAt when the transition was recorded
 */
public record CampaignStatusChanged(UUID campaignId, String previousStatus, String newStatus, Instant occurredAt) {

    /** Validates required fields at construction time so invalid lifecycle events fail fast. */
    public CampaignStatusChanged {
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        if (previousStatus == null || previousStatus.isBlank()) {
            throw new IllegalArgumentException("previousStatus must not be blank");
        }
        if (newStatus == null || newStatus.isBlank()) {
            throw new IllegalArgumentException("newStatus must not be blank");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
