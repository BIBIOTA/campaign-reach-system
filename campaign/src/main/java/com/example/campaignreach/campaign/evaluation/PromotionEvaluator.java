package com.example.campaignreach.campaign.evaluation;

import com.example.campaignreach.campaign.domain.CampaignType;

/**
 * Strategy that computes the actual promotion for a checkout (class-model {@code
 * PromotionEvaluator}, spec §4 / FR-002).
 *
 * <p>One implementation per {@link CampaignType}. Adding a new campaign type means adding a new
 * evaluator bean and registering it — existing evaluators are never modified (OCP). {@link
 * PromotionEvaluatorRegistry} resolves the matching evaluator by {@link #supports()}.
 */
public interface PromotionEvaluator {

    /** The {@link CampaignType} this evaluator computes promotions for. */
    CampaignType supports();

    /**
     * Computes the promotion outcome for the given checkout context.
     *
     * @param ctx the checkout context (subtotal + typed rule config)
     * @return the computed {@link PromotionResult}; a not-applicable result rather than an exception
     *     when the promotion does not apply
     */
    PromotionResult evaluate(CartContext ctx);
}
