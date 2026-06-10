package com.example.campaignreach.campaign.evaluation;

import com.example.campaignreach.campaign.domain.CampaignType;
import org.springframework.stereotype.Component;

/**
 * {@link ReachTriggerEvaluator} that judges by scheduled timing (class-model {@code
 * ScheduledTriggerEvaluator}, spec §4 / FR-008).
 *
 * <p>Triggers reach when the scheduled cycle in the context is {@link TriggerContext#due() due}. The
 * actual cycle scan / scheduling (ShedLock, cron) is a section-6 concern and is intentionally not
 * built here — this strategy only judges a single already-resolved occasion.
 *
 * <p>Scoped to {@link CampaignType#DISCOUNT} for MVP: a time-driven discount campaign reaches its
 * audience when its scheduled cycle comes due.
 */
@Component
public class ScheduledTriggerEvaluator implements ReachTriggerEvaluator {

    @Override
    public CampaignType supports() {
        return CampaignType.DISCOUNT;
    }

    @Override
    public TriggerKind kind() {
        return TriggerKind.SCHEDULED;
    }

    @Override
    public boolean shouldTrigger(TriggerContext ctx) {
        // Time-driven: trigger only when the scheduled cycle is due now. No cart context needed.
        return ctx.due();
    }
}
