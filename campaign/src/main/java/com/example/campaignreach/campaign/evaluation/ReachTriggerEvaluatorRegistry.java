package com.example.campaignreach.campaign.evaluation;

import com.example.campaignreach.campaign.domain.CampaignType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves and dispatches to the {@link ReachTriggerEvaluator} for a {@link TriggerContext}, with
 * per-determination exception isolation (spec §6 觸發判定例外隔離 / FR-008).
 *
 * <p>Spring injects every {@link ReachTriggerEvaluator} bean. They are indexed by the pair
 * ({@link ReachTriggerEvaluator#kind() kind}, {@link ReachTriggerEvaluator#supports() campaignType})
 * so dispatch first selects the candidate by trigger timing (SCHEDULED vs EVENT) and then gates by
 * campaign type — see the design note on {@link ReachTriggerEvaluator}.
 *
 * <p><strong>Exception isolation.</strong> {@link #evaluate(TriggerContext)} never propagates a
 * failure: if the underlying evaluator throws, the determination is recorded as {@link
 * TriggerDecision.Outcome#SKIPPED} with the reason and logged. {@link #evaluateBatch(List)} applies
 * this per item, so one bad determination does not abort the rest of the batch.
 */
@Component
public class ReachTriggerEvaluatorRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ReachTriggerEvaluatorRegistry.class);

    private final Map<EvaluatorKey, ReachTriggerEvaluator> evaluatorsByKey;

    /**
     * @param evaluators all {@link ReachTriggerEvaluator} beans on the classpath
     * @throws IllegalStateException if two evaluators claim the same (kind, campaignType) pair
     */
    public ReachTriggerEvaluatorRegistry(List<ReachTriggerEvaluator> evaluators) {
        this.evaluatorsByKey = evaluators.stream()
                .collect(Collectors.toUnmodifiableMap(e -> new EvaluatorKey(e.kind(), e.supports()), e -> e, (a, b) -> {
                    throw new IllegalStateException(
                            "Duplicate ReachTriggerEvaluator for " + new EvaluatorKey(a.kind(), a.supports()));
                }));
    }

    /**
     * Determines whether the campaign should trigger reach for the given occasion, isolating any
     * evaluator failure as a {@link TriggerDecision.Outcome#SKIPPED} decision.
     *
     * @param ctx the trigger context (scheduled cycle or behavior event, no cart data)
     * @return the determination; {@link TriggerDecision.Outcome#SKIPPED} with a reason if no evaluator
     *     is registered for the context or the evaluator throws — never propagates
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // deliberate broad catch: per-item exception isolation
    public TriggerDecision evaluate(TriggerContext ctx) {
        Optional<ReachTriggerEvaluator> evaluator = find(ctx.kind(), ctx.campaignType());
        if (evaluator.isEmpty()) {
            String reason = "no ReachTriggerEvaluator for kind=" + ctx.kind() + ", type=" + ctx.campaignType();
            LOG.warn("Trigger determination skipped: {}", reason);
            return TriggerDecision.skipped(reason);
        }
        try {
            return TriggerDecision.of(evaluator.get().shouldTrigger(ctx));
        } catch (RuntimeException ex) {
            String reason = "evaluator threw: " + ex.getMessage();
            LOG.warn(
                    "Trigger determination skipped for kind={}, type={}: {}",
                    ctx.kind(),
                    ctx.campaignType(),
                    reason,
                    ex);
            return TriggerDecision.skipped(reason);
        }
    }

    /**
     * Determines triggers for a batch, isolating per-item failures so a single skipped determination
     * does not affect the others (spec §6 不影響同批其他對象).
     *
     * @param contexts the trigger contexts to evaluate
     * @return one {@link TriggerDecision} per input, in order
     */
    public List<TriggerDecision> evaluateBatch(List<TriggerContext> contexts) {
        return contexts.stream().map(this::evaluate).toList();
    }

    /** Returns the evaluator registered for the given trigger kind and campaign type, if any. */
    public Optional<ReachTriggerEvaluator> find(TriggerKind kind, CampaignType campaignType) {
        return Optional.ofNullable(evaluatorsByKey.get(new EvaluatorKey(kind, campaignType)));
    }

    /**
     * Index key pairing trigger timing with campaign type.
     *
     * @param kind the trigger timing
     * @param campaignType the campaign type
     */
    private record EvaluatorKey(TriggerKind kind, CampaignType campaignType) {}
}
