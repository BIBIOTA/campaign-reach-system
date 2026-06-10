/**
 * Campaign module — evaluation layer (Promotion / ReachTrigger evaluators).
 *
 * <p>Holds the {@link com.example.campaignreach.campaign.evaluation.PromotionEvaluator} strategy
 * (spec §4, FR-002): a per-{@link com.example.campaignreach.campaign.domain.CampaignType} evaluator
 * computing a {@link com.example.campaignreach.campaign.evaluation.PromotionResult} from a {@link
 * com.example.campaignreach.campaign.evaluation.CartContext} at checkout, resolved via {@link
 * com.example.campaignreach.campaign.evaluation.PromotionEvaluatorRegistry}. Adding a campaign type
 * means adding a new evaluator bean only (OCP). {@code FlashSalePromotionEvaluator} is an MVP stub
 * extension point (FR-019).
 *
 * <p>The {@code ReachTriggerEvaluator} strategy (when to trigger reach) is added by a later task.
 */
package com.example.campaignreach.campaign.evaluation;
