package com.example.campaignreach.campaign.domain.rule;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Flash-sale offer rule (CampaignType.FLASH_SALE) — class-model {@code FlashSaleRuleConfig}.
 *
 * <p>MVP extension-point type (FR-019): kept intentionally minimal — a flash sale price and an
 * optional per-customer purchase cap — but still a strongly-typed schema carrying {@code
 * schema_version} so it participates in validation and version-aware reads like the other rule
 * types.
 *
 * @param schemaVersion payload schema version (persisted as {@code schema_version})
 * @param salePrice the flash-sale unit price; must not be negative
 * @param purchaseLimit max units per customer; must be positive
 */
public record FlashSaleRuleConfig(
        @JsonProperty("schema_version") int schemaVersion,
        @NotNull(message = "salePrice must be provided")
                @DecimalMin(value = "0", message = "salePrice must not be negative")
                BigDecimal salePrice,
        @jakarta.validation.constraints.Positive(message = "purchaseLimit must be positive") int purchaseLimit)
        implements RuleConfig {

    /** Current schema version produced by this code. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    @Override
    public CampaignType campaignType() {
        return CampaignType.FLASH_SALE;
    }
}
