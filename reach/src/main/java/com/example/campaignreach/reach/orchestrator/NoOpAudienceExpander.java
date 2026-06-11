package com.example.campaignreach.reach.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder {@link AudienceExpander} for task 7.1 — the landing path is wired end-to-end without yet
 * resolving the audience or inserting tasks. It records that expansion <em>would</em> run and does
 * nothing else, so a landed batch stays in {@link ReachRequestStatus#PENDING}.
 *
 * <p>Task 7.3 contributes the real expander (paged fan-out into {@code reach_task}, status advance
 * PENDING → EXPANDING → DISPATCHING, {@code total_count} backfill) and replaces this no-op as the sole
 * {@link AudienceExpander} bean. Keeping this seam minimal avoids pulling task 7.2/7.3 scope into 7.1
 * (YAGNI).
 */
@Component
public class NoOpAudienceExpander implements AudienceExpander {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpAudienceExpander.class);

    @Override
    public void expand(ReachRequest reachRequest) {
        LOG.debug(
                "No-op audience expansion for reach_request {} (campaign {}); real fan-out arrives in task 7.3",
                reachRequest.getId(),
                reachRequest.getCampaignId());
    }
}
