package com.example.campaignreach.campaign.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campaignreach.campaign.domain.CampaignType;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Fast (no-DB) unit tests for the ReachTriggerEvaluator strategy (spec §4 / §6 / FR-008). Test names
 * map to the spec scenarios.
 */
class ReachTriggerEvaluatorTest {

    private final ScheduledTriggerEvaluator scheduledEvaluator = new ScheduledTriggerEvaluator();
    private final BehaviorTriggerEvaluator behaviorEvaluator = new BehaviorTriggerEvaluator();

    // --- 觸發判定（無購物車） ---

    @Nested
    class 觸發判定無購物車 {

        @Test
        void scheduledEvaluatorTriggersWhenCycleIsDue() {
            TriggerContext ctx = TriggerContext.scheduled(CampaignType.DISCOUNT, true);

            assertThat(scheduledEvaluator.shouldTrigger(ctx)).isTrue();
        }

        @Test
        void scheduledEvaluatorDoesNotTriggerWhenCycleIsNotDue() {
            TriggerContext ctx = TriggerContext.scheduled(CampaignType.DISCOUNT, false);

            assertThat(scheduledEvaluator.shouldTrigger(ctx)).isFalse();
        }

        @Test
        void behaviorEvaluatorTriggersWhenEventMatches() {
            TriggerContext ctx = TriggerContext.event(CampaignType.GIFT_ADDON, BehaviorTriggerEvaluator.LISTENED_EVENT);

            assertThat(behaviorEvaluator.shouldTrigger(ctx)).isTrue();
        }

        @Test
        void behaviorEvaluatorDoesNotTriggerWhenEventDoesNotMatch() {
            TriggerContext ctx = TriggerContext.event(CampaignType.GIFT_ADDON, "PRODUCT_VIEWED");

            assertThat(behaviorEvaluator.shouldTrigger(ctx)).isFalse();
        }
    }

    // --- 例外隔離 / 註冊 ---

    @Nested
    class 觸發判定例外隔離 {

        @Test
        void registryDispatchesByTriggerKindThenCampaignType() {
            ReachTriggerEvaluatorRegistry registry =
                    new ReachTriggerEvaluatorRegistry(List.of(scheduledEvaluator, behaviorEvaluator));

            assertThat(registry.find(TriggerKind.SCHEDULED, CampaignType.DISCOUNT))
                    .containsSame(scheduledEvaluator);
            assertThat(registry.find(TriggerKind.EVENT, CampaignType.GIFT_ADDON))
                    .containsSame(behaviorEvaluator);
        }

        @Test
        void registrySkipsWhenNoEvaluatorIsRegistered() {
            ReachTriggerEvaluatorRegistry registry = new ReachTriggerEvaluatorRegistry(List.of(scheduledEvaluator));

            TriggerDecision decision = registry.evaluate(TriggerContext.event(CampaignType.GIFT_ADDON, "X"));

            assertThat(decision.outcome()).isEqualTo(TriggerDecision.Outcome.SKIPPED);
            assertThat(decision.reason()).contains("no ReachTriggerEvaluator");
        }

        @Test
        void throwingEvaluatorIsRecordedAsSkippedWithoutAffectingOtherDeterminationsInBatch() {
            // A deliberately broken evaluator that throws for its (kind, type).
            ReachTriggerEvaluator broken = new ReachTriggerEvaluator() {
                @Override
                public CampaignType supports() {
                    return CampaignType.FLASH_SALE;
                }

                @Override
                public TriggerKind kind() {
                    return TriggerKind.SCHEDULED;
                }

                @Override
                public boolean shouldTrigger(TriggerContext ctx) {
                    throw new IllegalStateException("boom");
                }
            };
            ReachTriggerEvaluatorRegistry registry =
                    new ReachTriggerEvaluatorRegistry(List.of(scheduledEvaluator, behaviorEvaluator, broken));

            List<TriggerContext> batch = List.of(
                    TriggerContext.scheduled(CampaignType.DISCOUNT, true), // -> TRIGGER
                    TriggerContext.scheduled(CampaignType.FLASH_SALE, true), // -> broken throws -> SKIPPED
                    TriggerContext.event(
                            CampaignType.GIFT_ADDON, BehaviorTriggerEvaluator.LISTENED_EVENT)); // -> TRIGGER

            List<TriggerDecision> decisions = registry.evaluateBatch(batch);

            assertThat(decisions).hasSize(3);
            assertThat(decisions.get(0).outcome()).isEqualTo(TriggerDecision.Outcome.TRIGGER);
            assertThat(decisions.get(1).outcome()).isEqualTo(TriggerDecision.Outcome.SKIPPED);
            assertThat(decisions.get(1).reason()).contains("boom");
            // The failure did not abort the batch — the third determination still completed.
            assertThat(decisions.get(2).outcome()).isEqualTo(TriggerDecision.Outcome.TRIGGER);
        }
    }
}
