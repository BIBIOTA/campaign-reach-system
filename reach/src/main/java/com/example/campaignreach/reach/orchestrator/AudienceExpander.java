package com.example.campaignreach.reach.orchestrator;

/**
 * Seam for audience resolution + fan-out, keeping the landing path ({@link ReachRequestLander})
 * decoupled from the expansion mechanics.
 *
 * <p>Once a {@link ReachRequest} is landed (or resumed) in {@link ReachRequestStatus#PENDING} /
 * {@link ReachRequestStatus#EXPANDING}, the orchestrator hands it to an {@code AudienceExpander} to
 * resolve {@code targetSpec} and insert the per-recipient reach_task rows, moving the batch through
 * EXPANDING -&gt; DISPATCHING and backfilling {@code total_count}. The sole implementation is
 * {@link PagedAudienceExpander} (paged ON CONFLICT inserts, crash-resumable fan-out).
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
