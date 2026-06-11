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
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
 *   <li><strong>大量發送不拖垮其他活動</strong> — a second campaign's tasks share the same EMAIL dispatch
 *       queue as a big campaign's heavy fan-out, and <em>two workers drain that shared queue
 *       concurrently</em>. The second campaign is served (every task converges to SENT, not starved) and
 *       its config row is never mutated. This proves the isolation guarantee the DB dispatch layer
 *       actually provides: {@code FOR UPDATE SKIP LOCKED} lets concurrent workers claim <em>disjoint</em>
 *       rows without blocking each other, so a heavy campaign does not stall another's progress. (The
 *       claim is channel-wide and FIFO-by-{@code created_at} — it does <strong>not</strong> partition by
 *       campaign; per-campaign hot-partition avoidance lives at the request layer, where {@code
 *       reach.requested} is partitioned on {@code reach_request_id} rather than {@code campaign_id}, see
 *       design.md §9 — a Kafka-config concern not exercised by this DB-level load test.)
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

    /**
     * Tasks for the second campaign that shares the EMAIL queue during the concurrent-drain isolation
     * test — enough that starvation would be observable, few enough to stay fast.
     */
    private static final int OTHER_CAMPAIGN_TASKS = 500;

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
     * Scenario: 大量發送不拖垮其他活動 — a big campaign's heavy fan-out and a second campaign's tasks share
     * the same EMAIL dispatch queue, and <em>two workers drain it concurrently</em>. A rendezvous on each
     * worker's first send proves both concurrently hold a disjoint claimed batch ({@code FOR UPDATE SKIP
     * LOCKED} is non-blocking — the second claimer never waits on the first's locked rows); the second
     * campaign is fully served (every task SENT, not starved) and its config row is never mutated.
     *
     * <p>This deliberately replaces an earlier "drain the big campaign, then check the other stayed
     * PENDING" shape: that only held by {@code created_at} FIFO luck and mis-attributed the isolation to a
     * non-existent per-campaign claim partition. The claim ({@link ReachTaskDispatchDao#claimBatch}) is in
     * fact channel-wide and FIFO — the isolation it provides is non-blocking concurrent claiming, which is
     * exactly what this test now exercises.
     */
    @Test
    void heavySendDoesNotStarveOtherCampaigns() throws InterruptedException {
        // Big campaign: a real (representative) fan-out so the shared EMAIL queue has heavy work to chew on.
        UUID bigCampaignId = seedCampaign("RUNNING");
        ReachRequest bigRequest = landedRequest(bigCampaignId, "cycle-BIG");
        int bigCount = Math.min(RECIPIENT_COUNT, 5_000); // representative heavy load; keeps the isolation test fast
        List<Recipient> bigRecipients = IntStream.range(0, bigCount)
                .mapToObj(i -> new Recipient(new UUID(0L, i)))
                .toList();
        AudienceResolver resolver = mock(AudienceResolver.class);
        when(resolver.resolve(any(TargetSpec.class))).thenReturn(bigRecipients);
        expander(resolver).expand(bigRequest);

        // Second campaign: its own request + its own PENDING tasks, sharing the same EMAIL queue.
        UUID otherCampaignId = seedCampaign("RUNNING");
        UUID otherRequestId = seedRequestRow(otherCampaignId, "cycle-OTHER");
        List<UUID> otherTaskIds = IntStream.range(0, OTHER_CAMPAIGN_TASKS)
                .mapToObj(i -> seedTask(otherRequestId, otherCampaignId, "cycle-OTHER"))
                .toList();
        String otherConfigBefore = campaignFingerprint(otherCampaignId);
        int totalTasks = bigCount + OTHER_CAMPAIGN_TASKS;

        // Two workers drain the shared queue concurrently. The rendezvous on each worker's FIRST send forces
        // both to have claimed a disjoint batch at the same time before either proceeds: if a regression made
        // claiming block (e.g. losing SKIP LOCKED), the second worker would never reach its first send and the
        // gate would time out (recorded in gateFailure, asserted after join). batchSize is small so neither
        // worker drains the queue in a single poll, guaranteeing the two claims interleave.
        CyclicBarrier firstSendGate = new CyclicBarrier(2);
        AtomicReference<Throwable> gateFailure = new AtomicReference<>();
        AtomicLong sentByA = new AtomicLong();
        AtomicLong sentByB = new AtomicLong();
        int concurrentBatch = 100;
        ReachTaskDispatcher workerA =
                dispatcher(new RendezvousStubChannelAdapter(sentByA, firstSendGate, gateFailure), concurrentBatch);
        ReachTaskDispatcher workerB =
                dispatcher(new RendezvousStubChannelAdapter(sentByB, firstSendGate, gateFailure), concurrentBatch);

        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Thread a = new Thread(() -> drainSharedQueue(workerA, totalTasks, workerFailure), "load-worker-A");
        Thread b = new Thread(() -> drainSharedQueue(workerB, totalTasks, workerFailure), "load-worker-B");
        a.start();
        b.start();
        a.join(Duration.ofMinutes(2).toMillis());
        b.join(Duration.ofMinutes(2).toMillis());

        // ---- Liveness: both workers ran concurrently and the drain terminated ----
        assertThat(a.isAlive()).as("worker A drained and terminated").isFalse();
        assertThat(b.isAlive()).as("worker B drained and terminated").isFalse();
        assertThat(workerFailure.get()).as("no worker thread threw").isNull();
        assertThat(gateFailure.get())
                .as("both workers concurrently held disjoint claimed batches (FOR UPDATE SKIP LOCKED is non-blocking)")
                .isNull();
        assertThat(sentByA.get()).as("worker A made progress").isPositive();
        assertThat(sentByB.get()).as("worker B made progress").isPositive();

        // ---- Disjoint, exactly-once claiming: every task sent exactly once across the two workers ----
        assertThat(sentByA.get() + sentByB.get())
                .as("each task claimed + sent exactly once across both workers (disjoint SKIP LOCKED claims)")
                .isEqualTo(totalTasks);

        // ---- No starvation: the second campaign was served, not dragged down by the heavy one ----
        assertThat(claimableCount(otherCampaignId))
                .as("second campaign fully drained, not starved")
                .isZero();
        assertThat(statusCount(otherCampaignId, "SENT"))
                .as("every second-campaign task was sent while the big campaign drained concurrently")
                .isEqualTo(OTHER_CAMPAIGN_TASKS);
        for (UUID taskId : otherTaskIds) {
            assertThat(taskStatus(taskId)).isEqualTo("SENT");
        }
        assertThat(statusCount(bigCampaignId, "SENT")).isEqualTo(bigCount);

        // ---- Config untouched: heavy sending never mutated the second campaign's config row ----
        assertThat(campaignFingerprint(otherCampaignId)).isEqualTo(otherConfigBefore);
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
        return dispatcher(adapter, 500);
    }

    private ReachTaskDispatcher dispatcher(ChannelAdapter adapter, int batchSize) {
        return new ReachTaskDispatcher(
                new ReachTaskDispatchDao(jdbcTemplate, transactionManager),
                new ChannelAdapterRegistry(List.of(adapter)),
                suppressionGuard,
                dlqPublisher,
                sendResultPublisher,
                new DispatcherProperties(batchSize, Duration.ofMinutes(5)));
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
        // Safety bound against an accidental infinite loop: a full drain at batchSize 500 needs ~N/500
        // polls, so N/100 (+1000 floor) is a generous ~5x headroom — not the batch size itself.
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

    /**
     * Worker body for the concurrent isolation drain: keep polling until the whole shared EMAIL queue is
     * drained (both campaigns). Any throwable is captured into {@code failure} rather than swallowed, so
     * the spawning test can assert it after the threads join. The same StubChannelAdapter-never-fails
     * convergence invariant as {@link #pumpUntilDrained} applies (every claimed row goes PENDING →
     * PROCESSING → SENT, so {@link #totalClaimable} tracks real claim eligibility).
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // capture any worker-thread failure for post-join assertion
    private void drainSharedQueue(ReachTaskDispatcher dispatcher, int totalTasks, AtomicReference<Throwable> failure) {
        try {
            // Safety bound: a full drain at batchSize 100 (split across two workers) needs ~totalTasks/100
            // polls; totalTasks/10 (+1000 floor) is a generous headroom, not the batch size itself.
            long maxPolls = (long) totalTasks / 10 + 1_000;
            long polls = 0;
            while (totalClaimable() > 0) {
                dispatcher.dispatchPoll();
                if (++polls > maxPolls) {
                    throw new IllegalStateException("shared-queue drain did not converge within " + maxPolls
                            + " polls; claimable left=" + totalClaimable());
                }
            }
        } catch (RuntimeException t) {
            failure.compareAndSet(null, t);
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

    /** Channel-wide claimable count (all campaigns) — the termination signal for the concurrent drain. */
    private int totalClaimable() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reach_task WHERE status IN"
                        + " ('PENDING'::reach_task_status, 'RETRY_SCHEDULED'::reach_task_status)",
                Integer.class);
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
        sb.append("\n## Isolation (互不影響 / NFR-002)\n");
        sb.append("- DB dispatch layer: concurrent workers claim disjoint rows via FOR UPDATE SKIP LOCKED")
                .append(" — non-blocking, so a heavy campaign does not stall another's claims. The claim is")
                .append(" channel-wide and FIFO-by-created_at; it does NOT partition by campaign. Proven by")
                .append(" heavySendDoesNotStarveOtherCampaigns (concurrent two-worker drain).\n");
        sb.append("- Request layer: per-campaign hot-partitioning is avoided by partitioning reach.requested")
                .append(" on reach_request_id (not campaign_id) — Kafka config, design.md §9; not exercised by")
                .append(" this DB-level load test.\n");
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

    /**
     * A {@link StubChannelAdapter} variant whose <em>first</em> send rendezvous at a shared {@link
     * CyclicBarrier}, used by the concurrent isolation drain to prove both workers simultaneously hold a
     * disjoint claimed batch (i.e. {@code FOR UPDATE SKIP LOCKED} did not block the second claimer). On
     * timeout / broken barrier the failure is <em>recorded</em> (not thrown): throwing here would be caught
     * by the dispatcher and reroute the task to the retryable path, masking the signal — so the test
     * asserts {@code gateFailure} after the worker threads join instead.
     */
    private static final class RendezvousStubChannelAdapter implements ChannelAdapter {

        private static final long GATE_TIMEOUT_SECONDS = 60;

        private final AtomicLong sendCount;
        private final CyclicBarrier firstSendGate;
        private final AtomicReference<Throwable> gateFailure;
        private final AtomicBoolean gatePassed = new AtomicBoolean();

        RendezvousStubChannelAdapter(
                AtomicLong sendCount, CyclicBarrier firstSendGate, AtomicReference<Throwable> gateFailure) {
            this.sendCount = sendCount;
            this.firstSendGate = firstSendGate;
            this.gateFailure = gateFailure;
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
            if (gatePassed.compareAndSet(false, true)) {
                try {
                    firstSendGate.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    gateFailure.compareAndSet(null, e);
                } catch (BrokenBarrierException | TimeoutException e) {
                    gateFailure.compareAndSet(null, e);
                }
            }
            long count = sendCount.incrementAndGet();
            return new SendResult("stub-" + count);
        }
    }
}
