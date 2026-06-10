package com.example.campaignreach.campaign.evaluation;

import com.example.campaignreach.campaign.domain.CampaignType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link PromotionEvaluator} for a {@link CampaignType} (spec §4 OCP, FR-002).
 *
 * <p>Spring injects every {@link PromotionEvaluator} bean and they are indexed by {@link
 * PromotionEvaluator#supports()}. Adding a new campaign type means adding a new evaluator bean — no
 * change to this registry or to existing evaluators (OCP).
 */
@Component
public class PromotionEvaluatorRegistry {

    private final Map<CampaignType, PromotionEvaluator> evaluatorsByType;

    /**
     * @param evaluators all {@link PromotionEvaluator} beans on the classpath
     * @throws IllegalStateException if two evaluators claim the same {@link CampaignType}
     */
    public PromotionEvaluatorRegistry(List<PromotionEvaluator> evaluators) {
        this.evaluatorsByType = evaluators.stream()
                .collect(Collectors.toUnmodifiableMap(PromotionEvaluator::supports, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("Duplicate PromotionEvaluator for type " + a.supports());
                }));
    }

    /**
     * Computes the promotion for a checkout by dispatching to the evaluator matching the context's
     * campaign type.
     *
     * @param ctx the checkout context
     * @return the computed {@link PromotionResult}; {@link PromotionResult#notApplicable()} when no
     *     evaluator is registered for the type (e.g. GIFT_ADDON has no promotion evaluator in MVP)
     */
    public PromotionResult evaluate(CartContext ctx) {
        return find(ctx.campaignType())
                .map(evaluator -> evaluator.evaluate(ctx))
                .orElseGet(PromotionResult::notApplicable);
    }

    /** Returns the evaluator registered for the given type, if any. */
    public Optional<PromotionEvaluator> find(CampaignType type) {
        return Optional.ofNullable(evaluatorsByType.get(type));
    }
}
