package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.CampaignStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Status-transition request for {@code POST /internal/campaigns/{id}/status} (task 4.2, FR-011).
 *
 * <p>Drives an operator-initiated lifecycle transition. Only the legal edges
 * (DRAFT→SCHEDULED→RUNNING→PAUSED/ENDED, PAUSED→RUNNING) are accepted; any other request is rejected
 * with HTTP 422. {@code version} is the required optimistic-lock guard (the value from a prior read):
 * a stale value fails with HTTP 409 instead of overwriting a concurrent edit (FR-001), mirroring
 * {@link UpdateCampaignRequest}.
 *
 * @param targetStatus the requested next lifecycle status
 * @param version the campaign version the client read; a stale value fails the transition with 409
 */
public record ChangeCampaignStatusRequest(
        @NotNull(message = "targetStatus must not be null") CampaignStatus targetStatus,
        @NotNull(message = "version must not be null") Integer version) {}
