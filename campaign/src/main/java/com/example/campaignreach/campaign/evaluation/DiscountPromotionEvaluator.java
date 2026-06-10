package com.example.campaignreach.campaign.evaluation;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.example.campaignreach.campaign.domain.rule.DiscountRuleConfig;
import com.example.campaignreach.campaign.domain.rule.ThresholdMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * {@link PromotionEvaluator} for {@link CampaignType#DISCOUNT} — computes the discount amount from a
 * {@link DiscountRuleConfig} at checkout (spec §4, FR-002).
 *
 * <ul>
 *   <li>{@code AMOUNT} — a fixed amount off, capped at the subtotal so the result is never negative.
 *   <li>{@code PERCENTAGE} — {@code subtotal × percentage / 100}, rounded {@link RoundingMode#HALF_UP}
 *       to 2 decimal places.
 * </ul>
 *
 * <p>The spend threshold (FR-003) gates the offer: {@link ThresholdMode#NONE} always applies; {@link
 * ThresholdMode#MIN_SPEND} applies only when {@code subtotal >= minSpend}, otherwise a
 * not-applicable result is returned.
 */
@Component
public class DiscountPromotionEvaluator implements PromotionEvaluator {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public CampaignType supports() {
        return CampaignType.DISCOUNT;
    }

    @Override
    public PromotionResult evaluate(CartContext ctx) {
        DiscountRuleConfig rule = (DiscountRuleConfig) ctx.ruleConfig();
        BigDecimal subtotal = ctx.subtotal();

        if (rule.thresholdMode() == ThresholdMode.MIN_SPEND
                && (rule.minSpend() == null || subtotal.compareTo(rule.minSpend()) < 0)) {
            return PromotionResult.notApplicable();
        }

        BigDecimal discount =
                switch (rule.kind()) {
                    case AMOUNT -> Objects.requireNonNull(rule.amount(), "amount is required for an AMOUNT discount")
                            .min(subtotal);
                    case PERCENTAGE -> subtotal.multiply(Objects.requireNonNull(
                                    rule.percentage(), "percentage is required for a PERCENTAGE discount"))
                            .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
                };

        return PromotionResult.discount(discount);
    }
}
