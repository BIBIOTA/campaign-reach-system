package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.CampaignStatus;
import java.util.UUID;

/**
 * Result of {@code POST /internal/campaigns}: the new campaign {@code id} for follow-up operations,
 * its {@code version} (for the first optimistic-lock read) and the {@code status} (always {@code
 * DRAFT} on create — FR-006).
 *
 * @param id new campaign identity for follow-up operations
 * @param version initial optimistic-lock version
 * @param status creation status (always {@code DRAFT})
 */
public record CreateCampaignResponse(UUID id, int version, CampaignStatus status) {}
