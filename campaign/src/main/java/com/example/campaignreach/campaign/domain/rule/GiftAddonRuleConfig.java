package com.example.campaignreach.campaign.domain.rule;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Gift / add-on offer rule (CampaignType.GIFT_ADDON) — class-model {@code GiftAddonRuleConfig}.
 *
 * <p>MVP extension-point type (FR-019): kept intentionally minimal — a gift SKU and quantity — but
 * still a strongly-typed schema carrying {@code schema_version} so it participates in validation and
 * version-aware reads like the other rule types.
 *
 * @param schemaVersion payload schema version (persisted as {@code schema_version})
 * @param giftSku SKU of the gift granted with the order
 * @param quantity number of gift units granted; must be positive
 */
public record GiftAddonRuleConfig(
        @JsonProperty("schema_version") int schemaVersion,
        @NotBlank(message = "giftSku must not be blank") String giftSku,
        @Positive(message = "gift quantity must be positive") int quantity)
        implements RuleConfig {

    /** Current schema version produced by this code. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    @Override
    public CampaignType campaignType() {
        return CampaignType.GIFT_ADDON;
    }
}
