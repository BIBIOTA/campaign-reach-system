package com.example.campaignreach.campaign.evaluation;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.example.campaignreach.campaign.domain.rule.RuleConfig;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable checkout input for a {@link PromotionEvaluator} (class-model {@code CartContext}).
 *
 * <p>Carries only what the implemented evaluators need to compute a promotion (YAGNI): the order
 * {@code subtotal} and the typed {@link RuleConfig} of the campaign being evaluated. The campaign
 * type is derived from {@link RuleConfig#campaignType()}, so a registry can resolve the matching
 * evaluator without a separate field (spec §4, FR-002).
 *
 * @param subtotal the order subtotal used for threshold checks and percentage discounts; must not be
 *     {@code null} or negative
 * @param ruleConfig the campaign's strongly-typed offer rule; must not be {@code null}
 */
public record CartContext(BigDecimal subtotal, RuleConfig ruleConfig) {

    /** Validates the checkout input. */
    public CartContext {
        Objects.requireNonNull(subtotal, "subtotal must not be null");
        Objects.requireNonNull(ruleConfig, "ruleConfig must not be null");
        if (subtotal.signum() < 0) {
            throw new IllegalArgumentException("subtotal must not be negative");
        }
    }

    /** The {@link CampaignType} of the campaign being evaluated, from the rule config. */
    public CampaignType campaignType() {
        return ruleConfig.campaignType();
    }
}
