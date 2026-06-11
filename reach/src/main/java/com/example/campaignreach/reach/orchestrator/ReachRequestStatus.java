package com.example.campaignreach.reach.orchestrator;

/**
 * Reach batch lifecycle status (ER {@code reach_request_status}).
 *
 * <p>Spec §5: PENDING / EXPANDING / DISPATCHING / DONE / FAILED / CANCELLED. The fan-out idempotency
 * guard (FR-013, NFR-003) hinges on this: a batch already in {@link #DISPATCHING} or {@link #DONE}
 * has completed audience expansion (and {@code total_count} backfill), so Kafka redelivery must skip
 * re-resolving the audience and re-inserting tasks; only {@link #PENDING} / {@link #EXPANDING}
 * batches are (re)entered for expansion.
 */
public enum ReachRequestStatus {
    PENDING,
    EXPANDING,
    DISPATCHING,
    DONE,
    FAILED,
    CANCELLED
}
