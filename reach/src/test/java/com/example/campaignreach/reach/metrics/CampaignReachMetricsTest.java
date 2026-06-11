package com.example.campaignreach.reach.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.campaignreach.reach.orchestrator.ReachTaskStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the campaign-level rate math (task 10.3, FR-018) — the「活動維度彙總」derivation,
 * including the zero-recipient (no division by zero) edge.
 */
class CampaignReachMetricsTest {

    private static final UUID CAMPAIGN = UUID.randomUUID();

    @Test
    void 活動維度彙總_computesDeliveredAndFailedRatesAndFullDistribution() {
        Map<ReachTaskStatus, Long> counts = new EnumMap<>(ReachTaskStatus.class);
        counts.put(ReachTaskStatus.PENDING, 3L);
        counts.put(ReachTaskStatus.SENT, 5L);
        counts.put(ReachTaskStatus.FAILED, 1L);
        counts.put(ReachTaskStatus.DLQ, 1L);
        // total = 10

        CampaignReachMetrics metrics = CampaignReachMetrics.fromStatusCounts(CAMPAIGN, counts);

        assertThat(metrics.campaignId()).isEqualTo(CAMPAIGN);
        assertThat(metrics.totalCount()).isEqualTo(10L);
        assertThat(metrics.deliveredRate()).isCloseTo(0.5, within(1e-9)); // 5/10
        assertThat(metrics.failedRate()).isCloseTo(0.2, within(1e-9)); // (1+1)/10
        // All seven statuses present; unseen ones are 0.
        assertThat(metrics.statusDistribution()).containsOnlyKeys(ReachTaskStatus.values());
        assertThat(metrics.statusDistribution().get(ReachTaskStatus.SENT)).isEqualTo(5L);
        assertThat(metrics.statusDistribution().get(ReachTaskStatus.PROCESSING)).isEqualTo(0L);
        assertThat(metrics.statusDistribution().get(ReachTaskStatus.CANCELLED)).isEqualTo(0L);
    }

    @Test
    void 活動維度彙總_zeroRecipientsYieldsZeroRatesWithoutDivisionByZero() {
        CampaignReachMetrics metrics = CampaignReachMetrics.fromStatusCounts(CAMPAIGN, Map.of());

        assertThat(metrics.totalCount()).isZero();
        assertThat(metrics.deliveredRate()).isZero();
        assertThat(metrics.failedRate()).isZero();
        assertThat(metrics.statusDistribution()).containsOnlyKeys(ReachTaskStatus.values());
        assertThat(metrics.statusDistribution().values()).allMatch(count -> count == 0L);
    }
}
