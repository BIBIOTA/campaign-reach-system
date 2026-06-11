package com.example.campaignreach.reach.orchestrator;

/**
 * Seam for audience resolution + fan-out (tasks 7.2 / 7.3), kept deliberately minimal here.
 *
 * <p>Task 7.1 owns only batch landing and the idempotency / skip decision: once a {@link ReachRequest}
 * is landed (or resumed) in {@link ReachRequestStatus#PENDING} / {@link ReachRequestStatus#EXPANDING},
 * the orchestrator hands it to an {@code AudienceExpander} to resolve {@code targetSpec} and insert
 * the per-recipient reach_task rows. The real implementation — which moves the batch through
 * EXPANDING → DISPATCHING and backfills {@code total_count} — arrives in task 7.3. The default bean
 * below is a no-op placeholder so the landing path is wired end-to-end without overbuilding.
 */
public interface AudienceExpander {

    /**
     * Resolves the audience for an already-landed PENDING/EXPANDING batch and fans it out into
     * reach_task rows. Called only after the batch is durably landed and the DISPATCHING/DONE skip
     * decision has decided to proceed.
     *
     * @param reachRequest the landed batch to expand
     */
    void expand(ReachRequest reachRequest);
}
