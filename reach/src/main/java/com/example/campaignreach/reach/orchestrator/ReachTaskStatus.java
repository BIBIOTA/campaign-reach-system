package com.example.campaignreach.reach.orchestrator;

/**
 * Per-recipient reach task lifecycle status (ER {@code reach_task_status}).
 *
 * <p>Spec §5: PENDING / PROCESSING / SENT / RETRY_SCHEDULED / FAILED / DLQ / CANCELLED. Task 7.3
 * (paged fan-out) only ever writes {@link #PENDING}: the audience expander inserts each surviving
 * recipient as a {@code ReachTask(PENDING)} and stops there. The remaining states are driven by the
 * dispatcher and retry/DLQ machinery in sections 8/9 — they are declared here so the enum mirrors the
 * full ER lifecycle, but task 7.3 deliberately does not transition into them.
 */
public enum ReachTaskStatus {
    PENDING,
    PROCESSING,
    SENT,
    RETRY_SCHEDULED,
    FAILED,
    DLQ,
    CANCELLED
}
