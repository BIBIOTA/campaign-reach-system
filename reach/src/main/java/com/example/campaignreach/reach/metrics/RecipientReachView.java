package com.example.campaignreach.reach.metrics;

import java.util.List;
import java.util.UUID;

/**
 * The per-recipient lookup response (task 10.3, FR-018, US-007, NFR-005): one user's reach
 * status(es) within a single campaign.
 *
 * <p>Because the same user can have several {@code reach_task} rows for one campaign (different send
 * cycles and/or channels), this wraps the full <em>set</em> of {@link RecipientReachStatus} entries
 * rather than a single status. An empty {@code statuses} list means the user has no reach task for
 * that campaign (a valid 200 response, not a 404 — the queried pair is simply not a recipient).
 *
 * @param campaignId the queried campaign
 * @param userId the queried recipient
 * @param statuses this recipient's task status entries for the campaign (possibly empty)
 */
public record RecipientReachView(UUID campaignId, UUID userId, List<RecipientReachStatus> statuses) {

    /** Defensively copies {@code statuses} so the response cannot be mutated post-construction. */
    public RecipientReachView {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }
}
