package com.example.campaignreach.reach.metrics;

import com.example.campaignreach.reach.orchestrator.ReachTaskStatus;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Application service for the read-only reach-metrics queries (task 10.3, FR-018, US-007).
 *
 * <p>Thin orchestration over {@link ReachMetricsDao}: it derives the campaign-level rates / status
 * distribution from the per-status task counts and assembles the per-recipient view. It holds no
 * state and performs no writes.
 */
@Service
public class ReachMetricsQueryService {

    private final ReachMetricsDao dao;

    public ReachMetricsQueryService(ReachMetricsDao dao) {
        this.dao = dao;
    }

    /**
     * 活動維度彙總: delivered rate, failed rate, and the per-status recipient distribution for a
     * campaign. The zero-recipient campaign yields a well-formed response with both rates {@code 0.0}
     * and every status count {@code 0} (no division by zero).
     *
     * @param campaignId the campaign to summarize
     * @return the campaign-level reach metrics
     */
    public CampaignReachMetrics campaignMetrics(UUID campaignId) {
        Map<ReachTaskStatus, Long> counts = dao.countTasksByStatus(campaignId);
        return CampaignReachMetrics.fromStatusCounts(campaignId, counts);
    }

    /**
     * 單筆收件人狀態: the reach status(es) of one recipient within a campaign. Returns an empty
     * {@code statuses} list (not an error) when the user is not a recipient of the campaign.
     *
     * @param campaignId the campaign to scope the lookup to
     * @param userId the recipient user
     * @return the PII-minimized per-recipient view
     */
    public RecipientReachView recipientStatus(UUID campaignId, UUID userId) {
        return new RecipientReachView(campaignId, userId, dao.findRecipientStatuses(campaignId, userId));
    }
}
