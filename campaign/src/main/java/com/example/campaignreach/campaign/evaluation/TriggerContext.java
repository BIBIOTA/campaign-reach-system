package com.example.campaignreach.campaign.evaluation;

import com.example.campaignreach.campaign.domain.CampaignType;
import java.util.Objects;

/**
 * Immutable input for a {@link ReachTriggerEvaluator} (class-model {@code TriggerContext}, spec §4 /
 * FR-008).
 *
 * <p>Describes the occasion on which reach might be triggered, carrying <strong>no cart / checkout
 * data</strong> — trigger determination is independent of {@link CartContext} (that is the separate
 * {@code PromotionEvaluator} concern). Only what the two evaluators actually read is carried (YAGNI):
 *
 * <ul>
 *   <li>{@code kind} — distinguishes a {@link TriggerKind#SCHEDULED} cycle from a behavior {@link
 *       TriggerKind#EVENT}; the registry uses it to pick the candidate evaluator.
 *   <li>{@code campaignType} — the campaign being evaluated; an evaluator only triggers for the type
 *       it {@link ReachTriggerEvaluator#supports() supports}.
 *   <li>{@code due} — for a {@link TriggerKind#SCHEDULED} context: whether the scheduled cycle is due
 *       now. Ignored for {@code EVENT}.
 *   <li>{@code eventType} — for a {@link TriggerKind#EVENT} context: the behavior event name (e.g.
 *       {@code CART_ABANDONED}). {@code null} for {@code SCHEDULED}.
 * </ul>
 *
 * @param kind the trigger occasion; must not be {@code null}
 * @param campaignType the campaign being evaluated; must not be {@code null}
 * @param due whether a scheduled cycle is due now (only meaningful when {@code kind == SCHEDULED})
 * @param eventType the behavior event name (only meaningful when {@code kind == EVENT}; may be {@code
 *     null} for a {@code SCHEDULED} context)
 */
public record TriggerContext(TriggerKind kind, CampaignType campaignType, boolean due, String eventType) {

    /** Validates the trigger input. */
    public TriggerContext {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(campaignType, "campaignType must not be null");
        if (kind == TriggerKind.EVENT) {
            Objects.requireNonNull(eventType, "eventType must not be null for an EVENT trigger");
        }
    }

    /** A scheduled-cycle trigger context for the given campaign type. */
    public static TriggerContext scheduled(CampaignType campaignType, boolean due) {
        return new TriggerContext(TriggerKind.SCHEDULED, campaignType, due, null);
    }

    /** A behavior-event trigger context for the given campaign type and event name. */
    public static TriggerContext event(CampaignType campaignType, String eventType) {
        return new TriggerContext(TriggerKind.EVENT, campaignType, false, eventType);
    }
}
