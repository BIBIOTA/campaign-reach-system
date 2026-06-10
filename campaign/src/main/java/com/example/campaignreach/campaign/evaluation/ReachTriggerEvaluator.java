package com.example.campaignreach.campaign.evaluation;

import com.example.campaignreach.campaign.domain.CampaignType;

/**
 * Strategy that decides whether a campaign should trigger reach for a scheduled cycle or a behavior
 * event (class-model {@code ReachTriggerEvaluator}, spec §4 / FR-008).
 *
 * <p>Independent of {@link CartContext}: trigger determination needs no cart / checkout data, only a
 * {@link TriggerContext} describing the occasion.
 *
 * <p><strong>Design note on {@link #supports()}.</strong> The class diagram fixes the contract as
 * {@code supports(): CampaignType}, so it is kept faithfully. But the two diagram strategies
 * ({@code ScheduledTriggerEvaluator} / {@code BehaviorTriggerEvaluator}) differ by trigger
 * <em>timing</em> (scheduled vs behavior), which {@code CampaignType} cannot express on its own.
 * Selection is therefore split: {@link ReachTriggerEvaluatorRegistry} first picks the candidate
 * evaluator by {@link TriggerContext#kind()} (SCHEDULED → scheduled evaluator, EVENT → behavior
 * evaluator), and {@code supports()} then gates by {@link CampaignType} so an evaluator only fires
 * for the campaign types it handles.
 */
public interface ReachTriggerEvaluator {

    /** The {@link CampaignType} this evaluator can make trigger determinations for. */
    CampaignType supports();

    /** The trigger occasion this evaluator handles (scheduled cycle vs behavior event). */
    TriggerKind kind();

    /**
     * Determines whether reach should be triggered for the given occasion. Needs no cart context.
     *
     * @param ctx the trigger context (scheduled-cycle or behavior-event occasion, no cart data)
     * @return {@code true} if the campaign should trigger reach for this occasion
     */
    boolean shouldTrigger(TriggerContext ctx);
}
