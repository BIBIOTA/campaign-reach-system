package com.example.campaignreach.campaign.domain;

/**
 * Campaign lifecycle status (ER {@code campaign_status}; class-model {@code CampaignStatus}).
 *
 * <p>Spec §4: DRAFT / SCHEDULED / RUNNING / PAUSED / ENDED.
 */
public enum CampaignStatus {
    DRAFT,
    SCHEDULED,
    RUNNING,
    PAUSED,
    ENDED
}
