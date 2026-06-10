package com.example.campaignreach.campaign.domain;

/**
 * Campaign offer type (ER {@code campaign_type}; class-model {@code CampaignType}).
 *
 * <p>The per-type validation of {@code ruleConfig} arrives in task 3.2.
 */
public enum CampaignType {
    DISCOUNT,
    GIFT_ADDON,
    FLASH_SALE
}
