package com.example.campaignreach.campaign.evaluation;

/**
 * The occasion that drives a {@link ReachTriggerEvaluator} determination (spec §4, FR-008).
 *
 * <p>Both paths converge on the same {@code reach.requested} topic downstream, but the trigger
 * occasion differs: a {@link #SCHEDULED} cycle (time-driven) versus a behavior {@link #EVENT}
 * (user-action-driven, e.g. cart abandoned). The {@link ReachTriggerEvaluatorRegistry} picks the
 * candidate evaluator by this kind.
 */
public enum TriggerKind {

    /** Time-driven: a scheduled cycle has come due for the campaign. */
    SCHEDULED,

    /** Behavior-driven: a user action event (e.g. cart abandoned) occurred. */
    EVENT
}
