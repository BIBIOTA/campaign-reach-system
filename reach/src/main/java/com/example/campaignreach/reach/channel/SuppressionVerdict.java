package com.example.campaignreach.reach.channel;

import com.example.campaignreach.reach.orchestrator.ReachTaskStatus;

/**
 * The outcome of a pre-send suppression check for one recipient + channel (design.md §10; FR-015).
 *
 * <p>This is a <strong>decision</strong>, not a side effect. The guard returns it; the Section-9
 * dispatcher applies it. When {@link #suppressed()} is true, the dispatcher MUST mark the
 * {@code reach_task} as {@link ReachTaskStatus#FAILED} (a non-retryable reason carried by {@link
 * #reason()}) and MUST NOT send. When false, the dispatcher proceeds with the send.
 *
 * <p>The verdict deliberately speaks the existing {@link ReachTaskStatus} lifecycle vocabulary
 * ({@link #failedStatus()} == {@link ReachTaskStatus#FAILED}) without performing any status-transition
 * machinery itself — writing the {@code reach_task} status is the dispatcher's responsibility
 * (Section 9, tasks 9.x), which owns the two-phase claim and status-update SQL.
 *
 * @param suppressed whether the recipient is suppressed on the checked channel
 * @param reason the suppression reason when {@code suppressed}; {@code null} when not suppressed
 */
public record SuppressionVerdict(boolean suppressed, SuppressionReason reason) {

    private static final SuppressionVerdict NOT_SUPPRESSED = new SuppressionVerdict(false, null);

    /** Invariant: a suppressed verdict must carry a reason; a non-suppressed one must not. */
    public SuppressionVerdict {
        if (suppressed && reason == null) {
            throw new IllegalArgumentException("a suppressed verdict must carry a reason");
        }
        if (!suppressed && reason != null) {
            throw new IllegalArgumentException("a non-suppressed verdict must not carry a reason");
        }
    }

    /** The verdict for a recipient not on the suppression list: the send may proceed. */
    public static SuppressionVerdict notSuppressed() {
        return NOT_SUPPRESSED;
    }

    /**
     * The verdict for a suppressed recipient: the task is to be marked FAILED and not sent.
     *
     * @param reason the non-retryable suppression reason (退訂 / 硬退信 / 投訴)
     */
    public static SuppressionVerdict suppressed(SuppressionReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        return new SuppressionVerdict(true, reason);
    }

    /**
     * The lifecycle status the dispatcher must apply when {@link #suppressed()} — always {@link
     * ReachTaskStatus#FAILED} (a non-retryable terminal state). Throws when not suppressed, since there
     * is no status to apply.
     */
    public ReachTaskStatus failedStatus() {
        if (!suppressed) {
            throw new IllegalStateException("a non-suppressed verdict has no FAILED status to apply");
        }
        return ReachTaskStatus.FAILED;
    }
}
