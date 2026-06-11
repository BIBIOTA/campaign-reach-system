package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.campaignreach.reach.audience.AudienceResolver;
import com.example.campaignreach.reach.audience.Recipient;
import com.example.campaignreach.reach.audience.TargetSpec;
import com.example.campaignreach.reach.audience.TargetSpecParser;
import com.example.campaignreach.reach.channel.ChannelAdapter;
import com.example.campaignreach.reach.channel.ReachMessage;
import com.example.campaignreach.reach.channel.SendResult;
import com.example.campaignreach.reach.channel.SuppressionGuard;
import com.example.campaignreach.reach.dispatcher.ChannelAdapterRegistry;
import com.example.campaignreach.reach.dispatcher.DispatcherProperties;
import com.example.campaignreach.reach.dispatcher.ReachDlqPublisher;
import com.example.campaignreach.reach.dispatcher.ReachTaskDispatchDao;
import com.example.campaignreach.reach.dispatcher.ReachTaskDispatcher;
import com.example.campaignreach.reach.dispatcher.SendResultPublisher;
import com.example.campaignreach.reach.orchestrator.ExpansionProperties;
import com.example.campaignreach.reach.orchestrator.PagedAudienceExpander;
import com.example.campaignreach.reach.orchestrator.ReachPlanChannelExtractor;
import com.example.campaignreach.reach.orchestrator.ReachRequest;
import com.example.campaignreach.reach.orchestrator.ReachRequestRepository;
import com.example.campaignreach.shared.event.Channel;
import com.example.campaignreach.shared.event.TriggerType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Full-chain reliability load test at 10萬筆級 (task 12.1, NFR-001 / NFR-002, US-008). Drives the real
 * production chain end to end against the Testcontainers PostgreSQL — reach_request landing →
 * {@link PagedAudienceExpander} fan-out → {@link ReachTaskDispatcher} two-phase claim+send+write-back —
 * and proves the two NFR scenarios from spec §「大量觸達可靠性與互不影響」:
 *
 * <ul>
 *   <li><strong>10 萬筆級全鏈路可靠跑完</strong> — a single campaign's {@code RECIPIENT_COUNT} fan-out is
 *       dispatched to terminal convergence: no leaked PENDING / PROCESSING / RETRY_SCHEDULED rows, and
 *       SENT + FAILED + DLQ = N. A load-test report (throughput / status distribution / resource usage)
 *       is produced as the baseline for evolving toward million-scale.
 *   <li><strong>大量發送不拖垮其他活動</strong> — a second campaign's reach_request + a handful of its own
 *       reach_task rows are seeded; the heavy dispatch loop (scoped to the big campaign's channel) leaves
 *       the second campaign's config row and tasks untouched and still independently claimable, proving
 *       isolation via the per-campaign/per-cycle keys and {@code FOR UPDATE SKIP LOCKED} disjoint claims.
 * </ul>
 *
 * <p>Auto-skipped without Docker via the inherited {@link RequiresDocker} condition, so {@code ./gradlew
 * check} stays green locally and runs fully in CI. The real wiring (parser, channel extractor,
 * JdbcTemplate, transaction manager, suppression guard) is used; only the {@link AudienceResolver} is
 * mocked (to materialize a controlled recipient set without an upstream member store) and the {@link
 * ChannelAdapter} is an in-test stub so the test measures the chain, not a real provider.
 *
 * <p><strong>Scale caveat (verification-pending).</strong> {@code RECIPIENT_COUNT} defaults to a value
 * sized to be genuinely 10萬筆級 yet complete in CI time; it is overridable via the {@code
 * reach.loadtest.recipients} system property. The actually-run scale is logged and written into the
 * report. The convergence assertions (full terminal convergence, zero leaked non-terminal rows) hold at
 * whatever N is run; a true sustained 1,000,000-row run is left as a separate capacity exercise.
 */
class ReachLoadReliabilityIntegrationTest extends AbstractIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ReachLoadReliabilityIntegrationTest.class);

    private static final String TARGET_SPEC = "{\"kind\":\"CONDITION\",\"conditions\":{\"region\":\"TW\"}}";
    private static final String REACH_PLAN =
            "{\"channel\":\"EMAIL\",\"templateRef\":\"welcome\",\"timing\":\"SCHEDULED\"}";

    /** The row count that qualifies a run as a full 10萬筆級 load test (shared by the default and the report flag). */
    private static final int FULL_SCALE_THRESHOLD = 100_000;

    /** The 10萬筆級 fan-out target; overridable for a heavier capacity run via {@code -Dreach.loadtest.recipients}. */
    private static final int RECIPIENT_COUNT = Integer.getInteger("reach.loadtest.recipients", FULL_SCALE_THRESHOLD);

    /** A handful of tasks for the second (isolation-control) campaign. */
    private static final int OTHER_CAMPAIGN_TASKS = 5;

    private static final String REPORT_DIR = "build/reports/load-test";
    private static final String REPORT_FILE = "task-12-reach-load-test.md";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TargetSpecParser targetSpecParser;

    @Autowired
    private ReachPlanChannelExtractor reachPlanChannelExtractor;

    @Autowired
    private ReachRequestRepository reachRequestRepository;

    @Autowired
    private SuppressionGuard suppressionGuard;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectProvider<ReachDlqPublisher> dlqPublisher;

    @Autowired
    private ObjectProvider<SendResultPublisher> sendResultPublisher;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM send_result");
        jdbcTemplate.update("DELETE FROM reach_task");
        jdbcTemplate.update("DELETE FROM reach_request");
        jdbcTemplate.update("DELETE FROM campaign");
    }

    /**
     * Scenario: 10 萬筆級全鏈路可靠跑完 — drive RECIPIENT_COUNT recipients through fan-out then dispatch to
     * terminal convergence, asserting no leaked non-terminal rows and SENT+FAILED+DLQ = N, and produce the
     * throughput / status-distribution / resource-usage report.
     */
    @Test
    void fullChainReliablyCompletesAt100kScaleAndConvergesAllStatuses() throws IOException {
        UUID campaignId = seedCampaign("RUNNING");
        ReachRequest request = landedRequest(campaignId, "cycle-LOAD");

        List<Recipient> recipients = IntStream.range(0, RECIPIENT_COUNT)
                .mapToObj(i -> new Recipient(new UUID(0L, i)))
                .toList();
        AudienceResolver resolver = mock(AudienceResolver.class);
        when(resolver.resolve(any(TargetSpec.class))).thenReturn(recipients);

        // ---- Phase 1: fan-out (reach_request → N reach_task(PENDING)) ----
        long fanOutStart = System.nanoTime();
        expander(resolver).expand(request);
        Duration fanOutElapsed = Duration.ofNanos(System.nanoTime() - fanOutStart);

        int pendingAfterFanOut = statusCount(campaignId, "PENDING");
        assertThat(pendingAfterFanOut).isEqualTo(RECIPIENT_COUNT);

        // ---- Phase 2: dispatch (claim+send+write-back) until terminal convergence ----
        AtomicLong sentByStub = new AtomicLong();
        ReachTaskDispatcher dispatcher = dispatcher(new StubChannelAdapter(sentByStub));

        long dispatchStart = System.nanoTime();
        pumpUntilDrained(dispatcher, campaignId);
        Duration dispatchElapsed = Duration.ofNanos(System.nanoTime() - dispatchStart);

        // ---- Convergence assertions: zero leaked non-terminal rows; terminal sum = N ----
        Map<String, Integer> distribution = statusDistribution(campaignId);
        int nonTerminal = distribution.getOrDefault("PENDING", 0)
                + distribution.getOrDefault("PROCESSING", 0)
                + distribution.getOrDefault("RETRY_SCHEDULED", 0);
        int terminal = distribution.getOrDefault("SENT", 0)
                + distribution.getOrDefault("FAILED", 0)
                + distribution.getOrDefault("DLQ", 0)
                + distribution.getOrDefault("CANCELLED", 0);
        assertThat(nonTerminal).as("no leaked non-terminal reach_task rows").isZero();
        assertThat(terminal)
                .as("all reach_task rows converged to a terminal state")
                .isEqualTo(RECIPIENT_COUNT);
        // The stub always succeeds → every row converges to SENT.
        assertThat(distribution.getOrDefault("SENT", 0)).isEqualTo(RECIPIENT_COUNT);

        // ---- Produce the load-test report (throughput / status distribution / resource usage) ----
        String report = buildReport(RECIPIENT_COUNT, fanOutElapsed, dispatchElapsed, distribution);
        writeAndLogReport(report);
    }

    /**
     * Scenario: 大量發送不拖垮其他活動 — while the big campaign is heavily dispatched, a second campaign's
     * config row and its own reach_task rows stay untouched and independently claimable, demonstrating
     * isolation through the per-campaign keys + SKIP LOCKED disjoint claiming.
     */
    @Test
    void heavySendDoesNotStarveOtherCampaigns() {
        // Big campaign: a real (smaller-but-representative) fan-out so the dispatch loop has work to chew on.
        UUID bigCampaignId = seedCampaign("RUNNING");
        ReachRequest bigRequest = landedRequest(bigCampaignId, "cycle-BIG");
        int bigCount = Math.min(RECIPIENT_COUNT, 5_000); // representative heavy load; keeps the isolation test fast
        List<Recipient> bigRecipients = IntStream.range(0, bigCount)
                .mapToObj(i -> new Recipient(new UUID(0L, i)))
                .toList();
        AudienceResolver resolver = mock(AudienceResolver.class);
        when(resolver.resolve(any(TargetSpec.class))).thenReturn(bigRecipients);
        expander(resolver).expand(bigRequest);

        // Second campaign: its own request + a handful of its own PENDING tasks (the isolation control set).
        UUID otherCampaignId = seedCampaign("RUNNING");
        UUID otherRequestId = seedRequestRow(otherCampaignId, "cycle-OTHER");
        List<UUID> otherTaskIds = IntStream.range(0, OTHER_CAMPAIGN_TASKS)
                .mapToObj(i -> seedTask(otherRequestId, otherCampaignId, "cycle-OTHER"))
                .toList();
        String otherConfigBefore = campaignFingerprint(otherCampaignId);

        // Heavy dispatch loop, scoped to the big campaign's drain.
        ReachTaskDispatcher dispatcher = dispatcher(new StubChannelAdapter(new AtomicLong()));
        pumpUntilDrained(dispatcher, bigCampaignId);

        // The second campaign's config row is byte-for-byte untouched (heavy sending never mutated it).
        assertThat(campaignFingerprint(otherCampaignId)).isEqualTo(otherConfigBefore);
        // Its tasks were never claimed/sent by the big-campaign drain — they stay PENDING and claimable.
        for (UUID taskId : otherTaskIds) {
            assertThat(taskStatus(taskId)).isEqualTo("PENDING");
        }
        assertThat(statusCount(otherCampaignId, "SENT")).isZero();

        // And they remain INDEPENDENTLY claimable now that the big campaign is fully drained.
        ReachTaskDispatchDao dao = new ReachTaskDispatchDao(jdbcTemplate, transactionManager);
        var claimed =
                dao.claimBatch("isolation-worker", List.of(Channel.EMAIL), 100, Duration.ofMinutes(5), Instant.now());
        assertThat(claimed).extracting(c -> c.taskId()).containsExactlyInAnyOrderElementsOf(otherTaskIds);
    }

    // ---- chain wiring ----

    private PagedAudienceExpander expander(AudienceResolver resolver) {
        return new PagedAudienceExpander(
                targetSpecParser,
                reachPlanChannelExtractor,
                resolver,
                jdbcTemplate,
                transactionManager,
                new ExpansionProperties(
                        new ExpansionProperties.Expansion(2_000), // large pages: real bulk-insert throughput
                        new ExpansionProperties.FrequencyCap(Duration.ofHours(24))));
    }

    private ReachTaskDispatcher dispatcher(ChannelAdapter adapter) {
        return new ReachTaskDispatcher(
                new ReachTaskDispatchDao(jdbcTemplate, transactionManager),
                new ChannelAdapterRegistry(List.of(adapter)),
                suppressionGuard,
                dlqPublisher,
                sendResultPublisher,
                new DispatcherProperties(500, Duration.ofMinutes(5)));
    }

    /**
     * Pump dispatchPoll() until the campaign has no claimable (PENDING/RETRY_SCHEDULED) rows left.
     *
     * <p>Termination relies on {@link StubChannelAdapter} never failing: every claimed row converges
     * PENDING → PROCESSING → SENT, so no row is ever left RETRY_SCHEDULED with a future {@code
     * next_retry_at}. That keeps {@link #claimableCount} (which counts all PENDING/RETRY_SCHEDULED
     * regardless of {@code next_retry_at}) equivalent to the DAO's real claim eligibility, so the loop
     * cannot exit while work is still pending. If the stub is ever changed to fail intermittently, this
     * exit condition must be revisited to honour {@code next_retry_at}.
     */
    private void pumpUntilDrained(ReachTaskDispatcher dispatcher, UUID campaignId) {
        // Generous bound proportional to scale (batchSize 500): guards against an accidental infinite loop
        // while leaving ample headroom for the real drain.
        long maxPolls = (long) RECIPIENT_COUNT / 100 + 1_000;
        long polls = 0;
        while (claimableCount(campaignId) > 0) {
            dispatcher.dispatchPoll();
            if (++polls > maxPolls) {
                throw new IllegalStateException("dispatch did not converge within " + maxPolls
                        + " polls; claimable left=" + claimableCount(campaignId));
            }
        }
    }

    // ---- seeding (mirrors ReachTaskDispatchDaoIntegrationTest's raw-SQL enum-cast pattern) ----

    private UUID seedCampaign(String status) {
        UUID campaignId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO campaign
                    (id, name, type, status, start_at, end_at, rule_config, target_spec, reach_plan,
                     version, created_at, updated_at)
                VALUES (?, ?, 'DISCOUNT'::campaign_type, ?::campaign_status, ?, ?, '{}'::jsonb, '{}'::jsonb,
                    ?::jsonb, 0, ?, ?)
                """,
                campaignId,
                "Campaign " + campaignId,
                status,
                Timestamp.from(now.minus(Duration.ofHours(1))),
                Timestamp.from(now.plus(Duration.ofHours(1))),
                REACH_PLAN,
                Timestamp.from(now),
                Timestamp.from(now));
        return campaignId;
    }

    /** Lands a reach_request via the real repository (carrying the frozen reach_plan_snapshot the claim join reads). */
    private ReachRequest landedRequest(UUID campaignId, String sendCycle) {
        ReachRequest request = new ReachRequest(
                UUID.randomUUID(),
                campaignId,
                TriggerType.SCHEDULED_BATCH,
                null,
                sendCycle,
                TARGET_SPEC,
                REACH_PLAN,
                Instant.now());
        return reachRequestRepository.saveAndFlush(request);
    }

    /** Seeds a reach_request row directly in DISPATCHING (the second-campaign control set). */
    private UUID seedRequestRow(UUID campaignId, String sendCycle) {
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reach_request
                    (id, campaign_id, trigger_type, send_cycle_key, reach_plan_snapshot, status, created_at)
                VALUES (?, ?, 'SCHEDULED_BATCH'::trigger_type, ?, ?::jsonb, 'DISPATCHING'::reach_request_status, ?)
                """,
                requestId,
                campaignId,
                sendCycle,
                REACH_PLAN,
                Timestamp.from(Instant.now()));
        return requestId;
    }

    private UUID seedTask(UUID requestId, UUID campaignId, String sendCycle) {
        UUID taskId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reach_task
                    (id, reach_request_id, campaign_id, user_id, send_cycle_key, channel, status,
                     retry_count, created_at)
                VALUES (?, ?, ?, ?, ?, 'EMAIL'::channel, 'PENDING'::reach_task_status, 0, ?)
                """,
                taskId,
                requestId,
                campaignId,
                UUID.randomUUID(),
                sendCycle,
                Timestamp.from(Instant.now()));
        return taskId;
    }

    // ---- queries ----

    private int claimableCount(UUID campaignId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reach_task WHERE campaign_id = ? AND status IN"
                        + " ('PENDING'::reach_task_status, 'RETRY_SCHEDULED'::reach_task_status)",
                Integer.class,
                campaignId);
        return count == null ? 0 : count;
    }

    private int statusCount(UUID campaignId, String status) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reach_task WHERE campaign_id = ? AND status = ?::reach_task_status",
                Integer.class,
                campaignId,
                status);
        return count == null ? 0 : count;
    }

    private String taskStatus(UUID taskId) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM reach_task WHERE id = ?", String.class, taskId);
    }

    private Map<String, Integer> statusDistribution(UUID campaignId) {
        return jdbcTemplate
                .query(
                        "SELECT status::text AS status, count(*) AS n FROM reach_task WHERE campaign_id = ?"
                                + " GROUP BY status",
                        (rs, rowNum) -> Map.entry(rs.getString("status"), rs.getInt("n")),
                        campaignId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** A stable digest of the second campaign's config row, to prove heavy sending never mutated it. */
    private String campaignFingerprint(UUID campaignId) {
        return jdbcTemplate.queryForObject(
                "SELECT status::text || '|' || version || '|' || updated_at FROM campaign WHERE id = ?",
                String.class,
                campaignId);
    }

    // ---- report ----

    private String buildReport(
            int recipientCount, Duration fanOut, Duration dispatch, Map<String, Integer> distribution) {
        Runtime runtime = Runtime.getRuntime();
        long usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        double fanOutRate = recipientCount / Math.max(1e-9, fanOut.toNanos() / 1_000_000_000.0);
        double dispatchRate = recipientCount / Math.max(1e-9, dispatch.toNanos() / 1_000_000_000.0);
        boolean fullScale = recipientCount >= FULL_SCALE_THRESHOLD;

        StringBuilder sb = new StringBuilder();
        sb.append("# Task 12.1 — Reach full-chain load-test report\n\n");
        sb.append("Baseline for evolving toward million-scale (NFR-001 / NFR-002, US-008).\n\n");
        sb.append("## Scale\n");
        sb.append("- Recipients (N): ").append(recipientCount).append('\n');
        sb.append("- Full 10萬筆級 run: ")
                .append(fullScale ? "yes" : "no (scaled down via -Dreach.loadtest.recipients)")
                .append('\n');
        sb.append("\n## Throughput (處理速率)\n");
        sb.append(String.format("- Fan-out: %.0f tasks/sec (elapsed %dms)%n", fanOutRate, fanOut.toMillis()));
        sb.append(String.format("- Dispatch: %.0f tasks/sec (elapsed %dms)%n", dispatchRate, dispatch.toMillis()));
        sb.append("\n## Status distribution (各狀態分布)\n");
        for (String s : List.of("PENDING", "PROCESSING", "RETRY_SCHEDULED", "SENT", "FAILED", "DLQ", "CANCELLED")) {
            sb.append("- ")
                    .append(s)
                    .append(": ")
                    .append(distribution.getOrDefault(s, 0))
                    .append('\n');
        }
        sb.append("\n## Resource usage (資源使用)\n");
        sb.append("- Wall time (fan-out + dispatch): ")
                .append(fanOut.plus(dispatch).toMillis())
                .append("ms\n");
        sb.append("- Used heap (coarse Runtime snapshot): ").append(usedHeapMb).append("MB\n");
        return sb.toString();
    }

    private void writeAndLogReport(String report) throws IOException {
        Path reportDir = Path.of(REPORT_DIR).toAbsolutePath();
        Files.createDirectories(reportDir);
        Path reportPath = reportDir.resolve(REPORT_FILE);
        Files.writeString(reportPath, report);
        LOG.info("Reach load-test report written to {}\n{}", reportPath, report);
    }

    /**
     * In-test {@link ChannelAdapter} stub: always available, sends instantly with a generated provider
     * message id, so the load test measures the persistence chain (claim + write-back SQL) rather than a
     * real provider's latency.
     */
    private static final class StubChannelAdapter implements ChannelAdapter {

        private final AtomicLong sendCount;

        StubChannelAdapter(AtomicLong sendCount) {
            this.sendCount = sendCount;
        }

        @Override
        public Channel channel() {
            return Channel.EMAIL;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public SendResult send(ReachMessage message) {
            long count = sendCount.incrementAndGet();
            // Monotonic counter (not UUID.randomUUID) keeps providerMessageId unique without a SecureRandom
            // call on the dispatch hot path, so the measured dispatch throughput reflects the persistence
            // chain rather than entropy gathering.
            return new SendResult("stub-" + count);
        }
    }
}
