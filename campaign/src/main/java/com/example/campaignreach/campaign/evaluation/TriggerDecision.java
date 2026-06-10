package com.example.campaignreach.campaign.evaluation;

import java.util.Objects;

/**
 * Immutable outcome of a single trigger determination by {@link ReachTriggerEvaluatorRegistry} (spec
 * §6 例外隔離 / FR-008).
 *
 * <p>Captures the per-determination result so a batch can isolate failures: a {@link
 * Outcome#SKIPPED} decision records the reason and never propagates the underlying exception, so one
 * bad evaluator does not abort the rest of the batch.
 *
 * @param outcome whether the determination triggers, does not trigger, or was skipped on error
 * @param reason the skip reason for {@link Outcome#SKIPPED}; {@code null} otherwise
 */
public record TriggerDecision(Outcome outcome, String reason) {

    /** The three possible results of a trigger determination. */
    public enum Outcome {
        /** The campaign should trigger reach for this occasion. */
        TRIGGER,
        /** The campaign should not trigger reach for this occasion. */
        NO_TRIGGER,
        /** The determination failed and was skipped; see {@link TriggerDecision#reason()}. */
        SKIPPED
    }

    /** Validates the decision. */
    public TriggerDecision {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome == Outcome.SKIPPED) {
            Objects.requireNonNull(reason, "reason must not be null for a SKIPPED decision");
        }
    }

    /** A decision derived from the {@code shouldTrigger} boolean (TRIGGER / NO_TRIGGER). */
    public static TriggerDecision of(boolean shouldTrigger) {
        return new TriggerDecision(shouldTrigger ? Outcome.TRIGGER : Outcome.NO_TRIGGER, null);
    }

    /** A skipped decision recording why the determination was isolated. */
    public static TriggerDecision skipped(String reason) {
        return new TriggerDecision(Outcome.SKIPPED, reason);
    }

    /** Whether this decision should produce reach (only {@link Outcome#TRIGGER}). */
    public boolean isTriggered() {
        return outcome == Outcome.TRIGGER;
    }
}
