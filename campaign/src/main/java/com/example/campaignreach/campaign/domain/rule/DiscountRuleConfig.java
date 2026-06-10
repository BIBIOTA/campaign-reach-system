package com.example.campaignreach.campaign.domain.rule;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Discount offer rule (CampaignType.DISCOUNT) — class-model {@code DiscountRuleConfig}.
 *
 * <p>Supports either a fixed {@code amount} off or a {@code percentage} off (selected by {@link
 * #kind}), gated by a spend threshold (FR-003): {@link ThresholdMode#NONE} (無門檻) or {@link
 * ThresholdMode#MIN_SPEND} (滿指定金額可用) with a {@code minSpend}.
 *
 * <p>Bean-validation guards reject negative discounts and percentages above 100 (FR-005). The
 * cross-field consistency between {@link #kind} and the populated value, and between {@link
 * #thresholdMode} and {@link #minSpend}, is checked by {@link RuleConfigValidator} so each failure
 * reports a clear reason.
 *
 * @param schemaVersion payload schema version (persisted as {@code schema_version})
 * @param kind whether the discount is an amount or a percentage
 * @param amount fixed currency amount off; required when {@code kind == AMOUNT}, must be {@code >= 0}
 * @param percentage percent off (0–100); required when {@code kind == PERCENTAGE}
 * @param thresholdMode spend-threshold gating mode
 * @param minSpend minimum order subtotal; required when {@code thresholdMode == MIN_SPEND}
 */
public record DiscountRuleConfig(
        @JsonProperty("schema_version") int schemaVersion,
        @NotNull(message = "discount kind must be AMOUNT or PERCENTAGE") DiscountKind kind,
        @DecimalMin(value = "0", message = "discount amount must not be negative") BigDecimal amount,
        @DecimalMin(value = "0", message = "discount percentage must not be negative")
                @DecimalMax(value = "100", message = "discount percentage must not exceed 100%")
                BigDecimal percentage,
        @NotNull(message = "thresholdMode must be NONE or MIN_SPEND") ThresholdMode thresholdMode,
        @DecimalMin(value = "0", message = "minSpend must not be negative") BigDecimal minSpend)
        implements RuleConfig {

    /** Current schema version produced by this code. */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    @Override
    public CampaignType campaignType() {
        return CampaignType.DISCOUNT;
    }
}
