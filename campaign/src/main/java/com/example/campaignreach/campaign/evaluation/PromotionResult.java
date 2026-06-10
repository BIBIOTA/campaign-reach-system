package com.example.campaignreach.campaign.evaluation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable outcome of a {@link PromotionEvaluator} for a checkout (class-model {@code
 * PromotionResult}).
 *
 * <p>The spec wording lists 折扣金額/贈品/加價購/閃購價 as possible outcomes (FR-002). To stay minimal while
 * keeping the extension points open, the result carries an {@code applicable} flag plus the discount
 * amount populated by {@link DiscountPromotionEvaluator}; gift / add-on / flash-price fields are not
 * built until an evaluator needs them (YAGNI, FR-019).
 *
 * @param applicable whether the promotion applies to this cart (a not-applicable result is the
 *     normal, non-error outcome when a threshold is unmet or for the flash-sale stub)
 * @param discountAmount the computed discount amount; {@link BigDecimal#ZERO} when not applicable;
 *     must not be {@code null}
 */
public record PromotionResult(boolean applicable, BigDecimal discountAmount) {

    private static final PromotionResult NOT_APPLICABLE = new PromotionResult(false, BigDecimal.ZERO);

    /** Validates the promotion result. */
    public PromotionResult {
        Objects.requireNonNull(discountAmount, "discountAmount must not be null");
    }

    /**
     * A not-applicable / no-benefit outcome (e.g. unmet threshold, or 已售罄／不適用 for the flash-sale
     * stub). This is a normal result, never an error.
     */
    public static PromotionResult notApplicable() {
        return NOT_APPLICABLE;
    }

    /**
     * An applicable discount outcome with the given amount off.
     *
     * @param discountAmount the discount amount; must not be {@code null}
     */
    public static PromotionResult discount(BigDecimal discountAmount) {
        return new PromotionResult(true, discountAmount);
    }
}
