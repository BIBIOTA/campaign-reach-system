package com.example.campaignreach.campaign.evaluation;

import com.example.campaignreach.campaign.domain.CampaignType;
import org.springframework.stereotype.Component;

/**
 * Stub-level {@link PromotionEvaluator} for {@link CampaignType#FLASH_SALE} — an MVP extension-point
 * placeholder (FR-019).
 *
 * <p>Real inventory checks and flash-sale pricing are explicitly out of MVP scope (spec §6 邊界). This
 * evaluator keeps the type and registration point in place so a full implementation can be added
 * later without modifying other evaluators (OCP). It returns a not-applicable (已售罄／不適用) {@link
 * PromotionResult} rather than throwing, so callers degrade gracefully instead of erroring.
 */
@Component
public class FlashSalePromotionEvaluator implements PromotionEvaluator {

    @Override
    public CampaignType supports() {
        return CampaignType.FLASH_SALE;
    }

    @Override
    public PromotionResult evaluate(CartContext ctx) {
        // MVP stub (FR-019): no real inventory or flash pricing — always 已售罄／不適用, never an error.
        return PromotionResult.notApplicable();
    }
}
