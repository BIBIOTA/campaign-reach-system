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
 * <p>Holds the {@link com.example.campaignreach.campaign.evaluation.ReachTriggerEvaluator} strategy
 * (spec §4, FR-008): decides whether a campaign should trigger reach for a scheduled cycle
 * ({@code ScheduledTriggerEvaluator}) or a behavior event ({@code BehaviorTriggerEvaluator}) from a
 * cart-free {@link com.example.campaignreach.campaign.evaluation.TriggerContext}. {@link
 * com.example.campaignreach.campaign.evaluation.ReachTriggerEvaluatorRegistry} dispatches by trigger
 * kind then campaign type and isolates per-determination failures as a {@link
 * com.example.campaignreach.campaign.evaluation.TriggerDecision} {@code SKIPPED} outcome (spec §6 例外隔離),
 * so one bad evaluator never aborts the rest of a batch. Emitting the {@code ReachRequested} event,
 * the scheduler, and the event consumer are section-6 concerns, not built here.
 */
package com.example.campaignreach.campaign.evaluation;
