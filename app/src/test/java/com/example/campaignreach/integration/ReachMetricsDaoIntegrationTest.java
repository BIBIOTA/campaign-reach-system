package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.campaignreach.reach.metrics.CampaignReachMetrics;
import com.example.campaignreach.reach.metrics.ReachMetricsDao;
import com.example.campaignreach.reach.metrics.ReachMetricsQueryService;
import com.example.campaignreach.reach.metrics.RecipientReachStatus;
import com.example.campaignreach.reach.metrics.RecipientReachView;
import com.example.campaignreach.reach.orchestrator.ReachTaskStatus;
import com.example.campaignreach.shared.event.Channel;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistence-level integration test for the read-only reach-metrics query slice (task 10.3,
 * FR-018, US-007, NFR-005), backed by a real PostgreSQL container with the Flyway-owned V1
 * (campaign) + V4 (reach_request) + V6 (reach_task) schema applied. Auto-skipped without Docker via
 * the inherited {@link RequiresDocker} condition (so {@code ./gradlew check} stays green locally;
 * the CI runner with Docker runs it fully).
 *
 * <p>This is where the {@code GROUP BY status} seven-way distribution SQL and the {@code
 * (campaign_id, user_id)} per-recipient SQL (with the {@code status::text} / {@code channel::text}
 * enum casts) are exercised against real Postgres — the controller slice test mocks the service, so
 * the real grouping/casting behavior is only verified here.
 */
class ReachMetricsDaoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ReachMetricsDao dao() {
        return new ReachMetricsDao(jdbcTemplate);
    }

    private ReachMetricsQueryService service() {
        return new ReachMetricsQueryService(dao());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM reach_task");
        jdbcTemplate.update("DELETE FROM reach_request");
        jdbcTemplate.update("DELETE FROM campaign");
    }

    private UUID seedCampaign() {
        UUID campaignId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO campaign
                    (id, name, type, status, start_at, end_at, rule_config, target_spec, reach_plan,
                     version, created_at, updated_at)
                VALUES (?, ?, 'DISCOUNT'::campaign_type, 'RUNNING'::campaign_status, ?, ?, '{}'::jsonb,
                    '{}'::jsonb, '{}'::jsonb, 0, ?, ?)
                """,
                campaignId,
                "Campaign " + campaignId,
                Timestamp.from(now.minus(Duration.ofHours(1))),
                Timestamp.from(now.plus(Duration.ofHours(1))),
                Timestamp.from(now),
                Timestamp.from(now));
        return campaignId;
    }

    private UUID seedRequest(UUID campaignId) {
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reach_request
                    (id, campaign_id, trigger_type, send_cycle_key, status, created_at)
                VALUES (?, ?, 'SCHEDULED_BATCH'::trigger_type, ?, 'DISPATCHING'::reach_request_status, ?)
                """,
                requestId,
                campaignId,
                "cycle-" + requestId,
                Timestamp.from(Instant.now()));
        return requestId;
    }

    private void seedTask(
            UUID requestId, UUID campaignId, UUID userId, String cycleKey, Channel channel, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO reach_task
                    (id, reach_request_id, campaign_id, user_id, send_cycle_key, channel, status,
                     retry_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?::channel, ?::reach_task_status, 0, ?)
                """,
                UUID.randomUUID(),
                requestId,
                campaignId,
                userId,
                cycleKey,
                channel.name(),
                status,
                Timestamp.from(Instant.now()));
    }

    /**
     * 活動維度彙總 — across all batches of a campaign, the per-status distribution is grouped from
     * reach_task and the delivered/failed rates are derived consistently. Spans two reach_request
     * batches and covers several of the seven statuses; statuses absent from the data come back as 0.
     */
    @Test
    void 活動維度彙總() {
        UUID campaignId = seedCampaign();
        UUID requestA = seedRequest(campaignId);
        UUID requestB = seedRequest(campaignId);
        // Batch A: 2 SENT, 1 PENDING, 1 FAILED
        seedTask(requestA, campaignId, UUID.randomUUID(), "cycle-A", Channel.EMAIL, "SENT");
        seedTask(requestA, campaignId, UUID.randomUUID(), "cycle-A", Channel.EMAIL, "SENT");
        seedTask(requestA, campaignId, UUID.randomUUID(), "cycle-A", Channel.EMAIL, "PENDING");
        seedTask(requestA, campaignId, UUID.randomUUID(), "cycle-A", Channel.EMAIL, "FAILED");
        // Batch B: 1 SENT, 1 DLQ, 1 CANCELLED, 1 PROCESSING
        seedTask(requestB, campaignId, UUID.randomUUID(), "cycle-B", Channel.SMS, "SENT");
        seedTask(requestB, campaignId, UUID.randomUUID(), "cycle-B", Channel.SMS, "DLQ");
        seedTask(requestB, campaignId, UUID.randomUUID(), "cycle-B", Channel.SMS, "CANCELLED");
        seedTask(requestB, campaignId, UUID.randomUUID(), "cycle-B", Channel.SMS, "PROCESSING");
        // total = 8: SENT=3, PENDING=1, FAILED=1, DLQ=1, CANCELLED=1, PROCESSING=1, RETRY_SCHEDULED=0

        CampaignReachMetrics metrics = service().campaignMetrics(campaignId);

        assertThat(metrics.totalCount()).isEqualTo(8L);
        assertThat(metrics.deliveredRate()).isCloseTo(3.0 / 8.0, within(1e-9));
        assertThat(metrics.failedRate()).isCloseTo(2.0 / 8.0, within(1e-9)); // FAILED + DLQ
        Map<ReachTaskStatus, Long> dist = metrics.statusDistribution();
        assertThat(dist).containsOnlyKeys(ReachTaskStatus.values());
        assertThat(dist.get(ReachTaskStatus.SENT)).isEqualTo(3L);
        assertThat(dist.get(ReachTaskStatus.PENDING)).isEqualTo(1L);
        assertThat(dist.get(ReachTaskStatus.FAILED)).isEqualTo(1L);
        assertThat(dist.get(ReachTaskStatus.DLQ)).isEqualTo(1L);
        assertThat(dist.get(ReachTaskStatus.CANCELLED)).isEqualTo(1L);
        assertThat(dist.get(ReachTaskStatus.PROCESSING)).isEqualTo(1L);
        assertThat(dist.get(ReachTaskStatus.RETRY_SCHEDULED)).isZero();
    }

    /** 活動維度彙總 — a campaign with no reach tasks yields zero rates and an all-zero distribution. */
    @Test
    void 活動維度彙總_zeroRecipients() {
        UUID campaignId = seedCampaign();

        CampaignReachMetrics metrics = service().campaignMetrics(campaignId);

        assertThat(metrics.totalCount()).isZero();
        assertThat(metrics.deliveredRate()).isZero();
        assertThat(metrics.failedRate()).isZero();
        assertThat(metrics.statusDistribution().values()).allMatch(c -> c == 0L);
    }

    /**
     * 單筆收件人狀態 — a recipient with several tasks (multiple channels / cycles) returns all of them,
     * PII-minimized (only campaign id, user id, channel, send-cycle key, status); another campaign's
     * task for the same user is excluded.
     */
    @Test
    void 單筆收件人狀態() {
        UUID campaignId = seedCampaign();
        UUID otherCampaign = seedCampaign();
        UUID requestId = seedRequest(campaignId);
        UUID otherRequest = seedRequest(otherCampaign);
        UUID userId = UUID.randomUUID();

        seedTask(requestId, campaignId, userId, "cycle-1", Channel.EMAIL, "SENT");
        seedTask(requestId, campaignId, userId, "cycle-1", Channel.SMS, "PENDING");
        // Same user, different campaign — must NOT appear in the scoped lookup.
        seedTask(otherRequest, otherCampaign, userId, "cycle-x", Channel.EMAIL, "FAILED");
        // Different user, same campaign — must NOT appear.
        seedTask(requestId, campaignId, UUID.randomUUID(), "cycle-1", Channel.EMAIL, "SENT");

        RecipientReachView view = service().recipientStatus(campaignId, userId);

        assertThat(view.campaignId()).isEqualTo(campaignId);
        assertThat(view.userId()).isEqualTo(userId);
        assertThat(view.statuses()).hasSize(2);
        assertThat(view.statuses())
                .extracting(RecipientReachStatus::channel, RecipientReachStatus::status)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(Channel.EMAIL, ReachTaskStatus.SENT),
                        org.assertj.core.groups.Tuple.tuple(Channel.SMS, ReachTaskStatus.PENDING));
        assertThat(view.statuses()).allSatisfy(s -> {
            assertThat(s.campaignId()).isEqualTo(campaignId);
            assertThat(s.userId()).isEqualTo(userId);
            assertThat(s.sendCycleKey()).isEqualTo("cycle-1");
        });
    }

    /** 單筆收件人狀態 — a user who is not a recipient of the campaign returns an empty status set, not an error. */
    @Test
    void 單筆收件人狀態_notARecipientYieldsEmptySet() {
        UUID campaignId = seedCampaign();

        RecipientReachView view = service().recipientStatus(campaignId, UUID.randomUUID());

        assertThat(view.statuses()).isEmpty();
    }
}
