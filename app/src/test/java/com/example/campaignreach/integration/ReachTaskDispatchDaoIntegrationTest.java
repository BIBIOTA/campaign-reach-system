package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campaignreach.reach.dispatcher.ClaimedTask;
import com.example.campaignreach.reach.dispatcher.ReachPlanTemplateExtractor;
import com.example.campaignreach.reach.dispatcher.ReachTaskDispatchDao;
import com.example.campaignreach.shared.event.Channel;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Persistence-level integration test for the two-phase dispatcher DAO (task 9.1), backed by a real
 * PostgreSQL container with the Flyway-owned V6 (reach_task) + V8 (send_result) schema applied.
 * Auto-skipped without Docker via the inherited {@link RequiresDocker} condition (so {@code
 * ./gradlew check} stays green locally; the CI runner with Docker runs it fully).
 *
 * <p>The dispatcher unit tests mock {@link ReachTaskDispatchDao} wholesale, so the highest-risk,
 * hardest-to-eyeball SQL is only really exercised here against real Postgres: the {@code FOR UPDATE
 * SKIP LOCKED} claim CTE, the dynamic {@code __CHANNELS__} placeholder substitution, the {@code
 * ?::reach_task_status} / {@code ?::channel} enum casts, the {@code reach_plan_snapshot::text} join,
 * the {@code next_retry_at <= ?} {@code IS NULL OR} branch, and the partial-unique {@code ON CONFLICT
 * (provider_message_id) ... DO NOTHING} send_result dedup.
 *
 * <p>The real wiring (JdbcTemplate, transaction manager, template extractor) is used against the
 * container DB; rows are seeded via raw SQL casting to the Postgres enum types.
 */
class ReachTaskDispatchDaoIntegrationTest extends AbstractIntegrationTest {

    private static final String REACH_PLAN =
            "{\"channel\":\"EMAIL\",\"templateRef\":\"welcome\",\"timing\":\"SCHEDULED\"}";
    private static final String WORKER_ID = "it-worker";
    private static final Duration LEASE = Duration.ofMinutes(5);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ReachPlanTemplateExtractor templateExtractor;

    private ReachTaskDispatchDao dao() {
        return new ReachTaskDispatchDao(jdbcTemplate, transactionManager, templateExtractor);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM send_result");
        jdbcTemplate.update("DELETE FROM reach_task");
        jdbcTemplate.update("DELETE FROM reach_request");
    }

    /** Seeds one reach_request (carrying the frozen reach_plan_snapshot the claim join reads). */
    private UUID seedRequest() {
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reach_request
                    (id, campaign_id, trigger_type, send_cycle_key, reach_plan_snapshot, status, created_at)
                VALUES (?, ?, 'SCHEDULED_BATCH'::trigger_type, ?, ?::jsonb, 'DISPATCHING'::reach_request_status, ?)
                """,
                requestId,
                UUID.randomUUID(),
                "cycle-" + requestId,
                REACH_PLAN,
                Timestamp.from(Instant.now()));
        return requestId;
    }

    /** Seeds one reach_task row with the given status/channel/next_retry_at, owned by {@code requestId}. */
    private UUID seedTask(UUID requestId, String status, Channel channel, Instant nextRetryAt) {
        UUID taskId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reach_task
                    (id, reach_request_id, campaign_id, user_id, send_cycle_key, channel, status,
                     retry_count, next_retry_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?::channel, ?::reach_task_status, ?, ?, ?)
                """,
                taskId,
                requestId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "cycle-" + taskId,
                channel.name(),
                status,
                0,
                nextRetryAt == null ? null : Timestamp.from(nextRetryAt),
                Timestamp.from(Instant.now()));
        return taskId;
    }

    private Map<String, Object> taskRow(UUID taskId) {
        return jdbcTemplate.queryForMap(
                "SELECT status::text AS status, locked_by, locked_until, processing_started_at,"
                        + " retry_count, next_retry_at, sent_at FROM reach_task WHERE id = ?",
                taskId);
    }

    /**
     * Assertion 1 — claim → PROCESSING + lease, and eligibility predicate including the {@code IS NULL
     * OR next_retry_at <= now} branch. A PENDING row with NULL next_retry_at IS claimed; a
     * RETRY_SCHEDULED row whose next_retry_at is already due IS claimed; a RETRY_SCHEDULED row whose
     * next_retry_at is in the future is NOT claimed; a row on an ineligible channel is NOT claimed.
     */
    @Test
    void claimBatchMarksEligibleRowsProcessingWithLeaseAndRespectsNextRetryAt() {
        UUID requestId = seedRequest();
        Instant now = Instant.now();

        UUID pendingNullRetry = seedTask(requestId, "PENDING", Channel.EMAIL, null);
        UUID retryDue = seedTask(requestId, "RETRY_SCHEDULED", Channel.EMAIL, now.minus(Duration.ofMinutes(1)));
        UUID retryFuture = seedTask(requestId, "RETRY_SCHEDULED", Channel.EMAIL, now.plus(Duration.ofMinutes(10)));
        UUID wrongChannel = seedTask(requestId, "PENDING", Channel.SMS, null);

        List<ClaimedTask> claimed = dao().claimBatch(WORKER_ID, List.of(Channel.EMAIL), 10, LEASE, now);

        assertThat(claimed).extracting(ClaimedTask::taskId).containsExactlyInAnyOrder(pendingNullRetry, retryDue);
        assertThat(claimed).allSatisfy(t -> {
            assertThat(t.channel()).isEqualTo(Channel.EMAIL);
            assertThat(t.templateRef()).isEqualTo("welcome"); // reach_plan_snapshot::text join + extractor
        });

        for (UUID id : List.of(pendingNullRetry, retryDue)) {
            Map<String, Object> row = taskRow(id);
            assertThat(row.get("status")).isEqualTo("PROCESSING");
            assertThat(row.get("locked_by")).isEqualTo(WORKER_ID);
            assertThat(row.get("locked_until")).isNotNull();
            assertThat(row.get("processing_started_at")).isNotNull();
        }
        // Future-retry and wrong-channel rows are untouched (stay claimable later / on their channel).
        assertThat(taskRow(retryFuture).get("status")).isEqualTo("RETRY_SCHEDULED");
        assertThat(taskRow(wrongChannel).get("status")).isEqualTo("PENDING");
    }

    /**
     * Assertion 2 — SKIP LOCKED disjointness. A second JDBC connection holds {@code SELECT ... FOR
     * UPDATE} on one eligible row inside an open transaction; the dispatcher's {@code claimBatch} must
     * SKIP that locked row and claim only the other one (no row claimed twice, no blocking).
     */
    @Test
    void claimBatchSkipsRowsLockedByAnotherTransaction() throws Exception {
        UUID requestId = seedRequest();
        Instant now = Instant.now();
        UUID lockedRow = seedTask(requestId, "PENDING", Channel.EMAIL, null);
        UUID freeRow = seedTask(requestId, "PENDING", Channel.EMAIL, null);

        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var ps = conn.prepareStatement("SELECT id FROM reach_task WHERE id = ? FOR UPDATE")) {
                ps.setObject(1, lockedRow);
                try (var rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                }

                // While the row is locked in the other transaction, claimBatch must skip it.
                List<ClaimedTask> claimed = dao().claimBatch(WORKER_ID, List.of(Channel.EMAIL), 10, LEASE, now);

                assertThat(claimed).extracting(ClaimedTask::taskId).containsExactly(freeRow);
            } finally {
                conn.rollback();
            }
        }
        // The previously-locked row remained PENDING (was skipped, not claimed).
        assertThat(taskRow(lockedRow).get("status")).isEqualTo("PENDING");
    }

    /**
     * Assertion 3 — markSent. Writes a send_result row (reach_task_id + provider_message_id + outcome +
     * occurred_at), transitions the task PROCESSING→SENT, and clears locked_by. A duplicate
     * provider_message_id (redelivery) converges to a single send_result row via the partial-unique ON
     * CONFLICT DO NOTHING.
     */
    @Test
    void markSentRecordsSendResultTransitionsSentAndDedupsDuplicateProviderMessageId() {
        UUID requestId = seedRequest();
        Instant now = Instant.now();
        UUID taskId = seedTask(requestId, "PENDING", Channel.EMAIL, null);

        ReachTaskDispatchDao dao = dao();
        dao.claimBatch(WORKER_ID, List.of(Channel.EMAIL), 10, LEASE, now); // → PROCESSING

        String providerMessageId = "provider-msg-" + taskId;
        dao.markSent(taskId, providerMessageId, now);
        // Redelivery with the SAME provider message id must not create a second send_result row.
        dao.markSent(taskId, providerMessageId, now.plus(Duration.ofSeconds(1)));

        Map<String, Object> row = taskRow(taskId);
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("locked_by")).isNull();
        assertThat(row.get("sent_at")).isNotNull();

        Integer sendResultCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM send_result WHERE reach_task_id = ? AND provider_message_id = ?",
                Integer.class,
                taskId,
                providerMessageId);
        assertThat(sendResultCount).isEqualTo(1);
        Map<String, Object> sendResult = jdbcTemplate.queryForMap(
                "SELECT outcome, occurred_at FROM send_result WHERE reach_task_id = ?", taskId);
        assertThat(sendResult.get("outcome")).isEqualTo("SENT");
        assertThat(sendResult.get("occurred_at")).isNotNull();
    }

    /**
     * Assertion 4 — scheduleRetry. Transitions PROCESSING→RETRY_SCHEDULED, increments retry_count, sets
     * next_retry_at to the backoff target, and clears the lease.
     */
    @Test
    void scheduleRetryTransitionsRetryScheduledIncrementsCountSetsNextRetryAndClearsLease() {
        UUID requestId = seedRequest();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID taskId = seedTask(requestId, "PENDING", Channel.EMAIL, null);

        ReachTaskDispatchDao dao = dao();
        dao.claimBatch(WORKER_ID, List.of(Channel.EMAIL), 10, LEASE, now); // → PROCESSING

        Instant nextRetryAt = now.plus(Duration.ofMinutes(1));
        dao.scheduleRetry(taskId, nextRetryAt, "transient-provider-error", now);

        Map<String, Object> row = taskRow(taskId);
        assertThat(row.get("status")).isEqualTo("RETRY_SCHEDULED");
        assertThat(row.get("retry_count")).isEqualTo(1);
        assertThat(row.get("locked_by")).isNull();
        assertThat(((Timestamp) row.get("next_retry_at")).toInstant()).isEqualTo(nextRetryAt);
    }

    /** Assertion 5 — markFailed. Transitions PROCESSING→FAILED and clears the lease. */
    @Test
    void markFailedTransitionsFailedAndClearsLease() {
        UUID requestId = seedRequest();
        Instant now = Instant.now();
        UUID taskId = seedTask(requestId, "PENDING", Channel.EMAIL, null);

        ReachTaskDispatchDao dao = dao();
        dao.claimBatch(WORKER_ID, List.of(Channel.EMAIL), 10, LEASE, now); // → PROCESSING

        dao.markFailed(taskId, "suppressed:UNSUBSCRIBE", now);

        Map<String, Object> row = taskRow(taskId);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("locked_by")).isNull();
        assertThat(row.get("locked_until")).isNull();
        assertThat(row.get("next_retry_at")).isNull();
    }
}
