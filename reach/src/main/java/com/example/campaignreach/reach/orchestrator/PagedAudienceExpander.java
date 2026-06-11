package com.example.campaignreach.reach.orchestrator;

import com.example.campaignreach.reach.audience.AudienceResolver;
import com.example.campaignreach.reach.audience.Recipient;
import com.example.campaignreach.reach.audience.TargetSpec;
import com.example.campaignreach.reach.audience.TargetSpecParser;
import com.example.campaignreach.shared.event.Channel;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real {@link AudienceExpander}: resolves the frozen targetSpec into recipients and fans one
 * {@code reach_request} out into N {@code reach_task(PENDING)} rows in committed pages (task 7.3,
 * spec §5, FR-014, NFR-003, US-006). Replaces the task-7.1 {@code NoOpAudienceExpander} as the sole
 * {@link AudienceExpander} bean.
 *
 * <p><strong>Idempotency — same cycle (unique constraint).</strong> Each page is inserted with
 * {@code INSERT ... ON CONFLICT (campaign_id, user_id, send_cycle_key, channel) DO NOTHING}. The
 * four-column unique key is the single source of truth for "同一活動同一週期同一人同一通道只一筆": a Kafka
 * redelivery of a half-expanded batch re-inserts the same rows, the already-written ones are silently
 * skipped, and the missing remainder is written — so fan-out converges to exactly N rows no matter how
 * many times it is replayed. (Spring Data JPA has no native upsert, hence the raw {@link JdbcTemplate}
 * batch INSERT with {@code ?::channel} / {@code ?::reach_task_status} enum casts.)
 *
 * <p><strong>Frequency capping — different cycle (separate from idempotency).</strong> Before
 * inserting a recipient, the expander skips the user if they already have a reach_task in a
 * <em>different</em> {@code send_cycle_key} within {@link ExpansionProperties#frequencyCapWindow()}
 * (predicate: {@code EXISTS reach_task WHERE user_id=? AND send_cycle_key <> :currentCycle AND
 * created_at >= now() - :window}). The {@code send_cycle_key <> :currentCycle} clause is the crux of
 * the 頻控/冪等 separation: the current cycle is deliberately excluded so a resume freely re-inserts the
 * current cycle's rows (that dedup is the unique constraint's job), while the cap only suppresses
 * over-reach from <em>other</em> events firing at the same user in a short window.
 *
 * <p><strong>Crash-resume boundary.</strong> The 100k fan-out is never wrapped in one transaction.
 * Each page runs in its own {@link TransactionTemplate} transaction (mirroring the per-item isolation
 * in {@code CampaignLifecycleScheduler}), so a crash mid-fan-out leaves the already-committed pages
 * durable and the redelivery only writes the remainder. The PENDING→EXPANDING advance and the final
 * EXPANDING→DISPATCHING + {@code total_count} backfill each run in their own short transaction too.
 */
@Component
public class PagedAudienceExpander implements AudienceExpander {

    private static final Logger LOG = LoggerFactory.getLogger(PagedAudienceExpander.class);

    private static final String INSERT_TASK_SQL =
            """
            INSERT INTO reach_task (
                id, reach_request_id, campaign_id, user_id, send_cycle_key, channel, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?::channel, ?::reach_task_status, ?)
            ON CONFLICT (campaign_id, user_id, send_cycle_key, channel) DO NOTHING
            """;

    /**
     * Frequency-cap predicate: has this user been reached in a DIFFERENT send cycle within the window?
     * The {@code send_cycle_key <> ?} clause keeps the cap orthogonal to the same-cycle unique
     * idempotency, so a resume of the current cycle is never falsely capped.
     */
    private static final String FREQ_CAP_EXISTS_SQL =
            """
            SELECT EXISTS (
                SELECT 1 FROM reach_task
                WHERE user_id = ?
                  AND send_cycle_key <> ?
                  AND created_at >= ?)
            """;

    private final TargetSpecParser targetSpecParser;
    private final ReachPlanChannelExtractor reachPlanChannelExtractor;
    private final AudienceResolver audienceResolver;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ExpansionProperties properties;

    /**
     * @param targetSpecParser parses the frozen targetSpec JSON into a typed spec
     * @param reachPlanChannelExtractor reads the dedup-key channel from the frozen reachPlan JSON
     * @param audienceResolver resolves the spec into a recipient list (reach-side, FR-007/FR-013)
     * @param jdbcTemplate auto-configured template used for the ON CONFLICT batch insert, the
     *     frequency-cap EXISTS reads, and the reach_request status/count updates
     * @param transactionManager backs the per-page (and per-status-update) short transactions
     * @param properties page size + frequency-cap window tunables
     */
    public PagedAudienceExpander(
            TargetSpecParser targetSpecParser,
            ReachPlanChannelExtractor reachPlanChannelExtractor,
            AudienceResolver audienceResolver,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ExpansionProperties properties) {
        this.targetSpecParser = targetSpecParser;
        this.reachPlanChannelExtractor = reachPlanChannelExtractor;
        this.audienceResolver = audienceResolver;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
    }

    @Override
    public void expand(ReachRequest reachRequest) {
        UUID reachRequestId = reachRequest.getId();
        UUID campaignId = reachRequest.getCampaignId();
        String sendCycleKey = reachRequest.getSendCycleKey();

        // (a) PENDING -> EXPANDING (idempotent: re-running on an already-EXPANDING resume is a no-op move).
        advanceToExpanding(reachRequestId);

        // (b) Parse the FROZEN snapshots: who (targetSpec) and which channel (reachPlan, dedup-key column).
        TargetSpec spec = targetSpecParser.parse(reachRequest.getTargetSpecSnapshot());
        Channel channel = reachPlanChannelExtractor.extract(reachRequest.getReachPlanSnapshot());
        List<Recipient> recipients = audienceResolver.resolve(spec);

        // (c)/(d) Page recipients; per page apply frequency capping then ON CONFLICT batch-insert.
        int pageSize = properties.pageSize();
        Instant freqCapFloor = Instant.now().minus(properties.frequencyCapWindow());
        for (int start = 0; start < recipients.size(); start += pageSize) {
            int end = Math.min(start + pageSize, recipients.size());
            List<Recipient> page = recipients.subList(start, end);
            insertPage(page, reachRequestId, campaignId, sendCycleKey, channel, freqCapFloor);
        }

        // (e) EXPANDING -> DISPATCHING and backfill total_count ONCE (actual rows for this request).
        advanceToDispatching(reachRequestId);

        LOG.info(
                "Expanded reach_request {} (campaign {}) into reach_task(PENDING) rows on channel {}",
                reachRequestId,
                campaignId,
                channel);
    }

    /** Moves the batch PENDING -> EXPANDING and stamps started_at, in its own short transaction. */
    private void advanceToExpanding(UUID reachRequestId) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                """
                UPDATE reach_request
                SET status = 'EXPANDING'::reach_request_status,
                    started_at = COALESCE(started_at, ?)
                WHERE id = ? AND status = 'PENDING'::reach_request_status
                """,
                Timestamp.from(Instant.now()),
                reachRequestId));
    }

    /**
     * Inserts one page in its own transaction: frequency-cap survivors are batch-inserted with ON
     * CONFLICT DO NOTHING. Committing per page is the crash-resume boundary — a crash leaves earlier
     * pages durable and redelivery writes only the missing remainder.
     */
    private void insertPage(
            List<Recipient> page,
            UUID reachRequestId,
            UUID campaignId,
            String sendCycleKey,
            Channel channel,
            Instant freqCapFloor) {
        transactionTemplate.executeWithoutResult(status -> {
            List<Object[]> batch = new ArrayList<>(page.size());
            Timestamp now = Timestamp.from(Instant.now());
            Timestamp freqFloor = Timestamp.from(freqCapFloor);
            for (Recipient recipient : page) {
                if (isFrequencyCapped(recipient.userId(), sendCycleKey, freqFloor)) {
                    continue;
                }
                batch.add(new Object[] {
                    UUID.randomUUID(),
                    reachRequestId,
                    campaignId,
                    recipient.userId(),
                    sendCycleKey,
                    channel.name(),
                    ReachTaskStatus.PENDING.name(),
                    now
                });
            }
            if (!batch.isEmpty()) {
                jdbcTemplate.batchUpdate(INSERT_TASK_SQL, batch);
            }
        });
    }

    /**
     * Frequency cap: true when the user already has a reach_task in a DIFFERENT send cycle within the
     * window. Deliberately excludes the current cycle so same-cycle idempotency stays the unique
     * constraint's responsibility, not the cap's.
     */
    private boolean isFrequencyCapped(UUID userId, String currentCycle, Timestamp freqCapFloor) {
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(FREQ_CAP_EXISTS_SQL, Boolean.class, userId, currentCycle, freqCapFloor));
    }

    /**
     * Completes fan-out: EXPANDING -> DISPATCHING and backfills total_count ONCE with the actual
     * reach_task row count for this request (idempotent — re-running on an already-DISPATCHING batch
     * matches no row).
     */
    private void advanceToDispatching(UUID reachRequestId) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                """
                UPDATE reach_request
                SET status = 'DISPATCHING'::reach_request_status,
                    total_count = (SELECT count(*) FROM reach_task WHERE reach_request_id = ?)
                WHERE id = ? AND status = 'EXPANDING'::reach_request_status
                """,
                reachRequestId,
                reachRequestId));
    }
}
