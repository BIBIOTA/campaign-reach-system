package com.example.campaignreach.reach.metrics;

import com.example.campaignreach.reach.orchestrator.ReachTaskStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Campaign-level reach metrics (task 10.3, FR-018, US-007): the「活動維度彙總」response.
 *
 * <p>Carries the delivered rate (sent / total), the failed rate (failed / total), and the full
 * per-{@link ReachTaskStatus} recipient distribution across <em>all</em> of the campaign's batches.
 * {@code reach_request} only folds task states into pending/sent/failed buckets, so the seven-way
 * distribution is computed from {@code reach_task} grouped by status; the rates are derived from
 * that same distribution so the numbers are internally consistent.
 *
 * <p>Rates are fractions in {@code [0, 1]}. The zero-recipient case is handled with no division by
 * zero — both rates are {@code 0.0} when {@code totalCount} is {@code 0}.
 *
 * @param campaignId the queried campaign
 * @param totalCount total reach tasks across all batches of the campaign (the rate denominator)
 * @param deliveredRate {@code SENT / total}, or {@code 0.0} when there are no recipients
 * @param failedRate {@code (FAILED + DLQ) / total}, or {@code 0.0} when there are no recipients
 * @param statusDistribution recipient count per {@link ReachTaskStatus} (all seven keys present)
 */
public record CampaignReachMetrics(
        UUID campaignId,
        long totalCount,
        double deliveredRate,
        double failedRate,
        Map<ReachTaskStatus, Long> statusDistribution) {

    /** Defensively copies {@code statusDistribution} so the response cannot be mutated post-construction. */
    public CampaignReachMetrics {
        statusDistribution = statusDistribution == null ? Map.of() : Map.copyOf(new EnumMap<>(statusDistribution));
    }

    /**
     * Builds the metrics from a raw per-status count map, deriving total and the delivered/failed
     * rates. Missing statuses default to {@code 0} so every {@link ReachTaskStatus} key is present.
     * {@code SENT} is delivered; {@code FAILED} and {@code DLQ} are both terminal failures.
     *
     * @param campaignId the queried campaign
     * @param counts recipient count per status (any subset of the enum; absent statuses count as 0)
     * @return the populated metrics with rates computed (rate = 0 when there are no recipients)
     */
    public static CampaignReachMetrics fromStatusCounts(UUID campaignId, Map<ReachTaskStatus, Long> counts) {
        Map<ReachTaskStatus, Long> distribution = new EnumMap<>(ReachTaskStatus.class);
        long total = 0L;
        for (ReachTaskStatus status : ReachTaskStatus.values()) {
            long count = counts.getOrDefault(status, 0L);
            distribution.put(status, count);
            total += count;
        }
        long delivered = distribution.get(ReachTaskStatus.SENT);
        long failed = distribution.get(ReachTaskStatus.FAILED) + distribution.get(ReachTaskStatus.DLQ);
        double deliveredRate = total == 0L ? 0.0 : (double) delivered / total;
        double failedRate = total == 0L ? 0.0 : (double) failed / total;
        return new CampaignReachMetrics(campaignId, total, deliveredRate, failedRate, distribution);
    }
}
