package com.example.campaignreach.campaign.evaluation;

import com.example.campaignreach.campaign.domain.CampaignType;
import org.springframework.stereotype.Component;

/**
 * {@link ReachTriggerEvaluator} that judges by a user behavior event (class-model {@code
 * BehaviorTriggerEvaluator}, spec §4 / FR-008).
 *
 * <p>Triggers reach when the incoming behavior {@link TriggerContext#eventType() event} matches the
 * one this campaign listens for — for MVP, the cart-abandoned ({@code CART_ABANDONED}) signal that a
 * gift / add-on campaign reacts to. The event ingestion / consumer is a section-6 concern and is not
 * built here — this strategy only judges a single already-resolved event occasion.
 *
 * <p>Scoped to {@link CampaignType#GIFT_ADDON} for MVP.
 */
@Component
public class BehaviorTriggerEvaluator implements ReachTriggerEvaluator {

    /** The behavior event this evaluator reacts to (cart-abandoned style). */
    static final String LISTENED_EVENT = "CART_ABANDONED";

    @Override
    public CampaignType supports() {
        return CampaignType.GIFT_ADDON;
    }

    @Override
    public TriggerKind kind() {
        return TriggerKind.EVENT;
    }

    @Override
    public boolean shouldTrigger(TriggerContext ctx) {
        // Behavior-driven: trigger only when the event matches what the campaign listens for.
        return LISTENED_EVENT.equals(ctx.eventType());
    }
}
