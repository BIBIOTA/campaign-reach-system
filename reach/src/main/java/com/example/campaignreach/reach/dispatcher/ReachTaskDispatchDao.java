package com.example.campaignreach.reach.dispatcher;

import com.example.campaignreach.shared.event.Channel;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistence for the two-phase {@code ReachTask} dispatch (task 9.1, spec「可靠發送與重試」, §5
 * NFR-002/NFR-003). Each public method is a single short transaction so the external send call always
 * runs <em>between</em> committed transactions and never holds a DB connection across the network call:
 *
 * <ul>
 *   <li><strong>Stage one — {@link #claimBatch}</strong>: {@code FOR UPDATE SKIP LOCKED} selects up to
 *       {@code batchSize} dispatchable rows ({@code status IN (PENDING, RETRY_SCHEDULED) AND
 *       next_retry_at <= now()}), marks them PROCESSING and stamps {@code locked_by/locked_until} (the
 *       lease), then commits immediately — releasing both the row locks and the connection. SKIP LOCKED
 *       makes concurrent workers claim disjoint rows without blocking, so no ShedLock is needed.
 *   <li><strong>Stage two — {@link #markSent} / {@link #scheduleRetry} / {@link #markFailed}</strong>:
 *       a fresh transaction writes back the outcome of the out-of-transaction send and clears the lease.
 * </ul>
 *
 * <p>Spring Data JPA has no {@code FOR UPDATE SKIP LOCKED} claim-and-mark, so this uses the raw
 * {@link JdbcTemplate} + {@code ?::reach_task_status} enum-cast pattern established by
 * {@code PagedAudienceExpander}.
 */
@Component
public class ReachTaskDispatchDao {

    /**
     * Stage-one claim: lock the next batch of dispatchable rows ({@code FOR UPDATE SKIP LOCKED} so
     * concurrent workers take disjoint rows), mark them PROCESSING + lease, and return the fields the
     * out-of-transaction send needs (joining the owning reach_request for the frozen reach plan). The
     * {@code idx_reach_task_dispatch(status, next_retry_at, created_at)} index backs the predicate;
     * oldest-first ordering keeps it FIFO-ish.
     */
    private static final String CLAIM_SQL_TEMPLATE =
            """
            WITH claimed AS (
                SELECT t.id
                FROM reach_task t
                WHERE t.status IN ('PENDING'::reach_task_status, 'RETRY_SCHEDULED'::reach_task_status)
                  AND (t.next_retry_at IS NULL OR t.next_retry_at <= ?)
                  AND t.channel IN (__CHANNELS__)
                ORDER BY t.created_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE reach_task u
            SET status = 'PROCESSING'::reach_task_status,
                locked_by = ?,
                locked_until = ?,
                processing_started_at = ?
            FROM claimed
            WHERE u.id = claimed.id
            RETURNING u.id, u.campaign_id, u.user_id, u.channel, u.send_cycle_key,
                (SELECT r.reach_plan_snapshot::text FROM reach_request r WHERE r.id = u.reach_request_id) AS reach_plan,
                COALESCE(u.retry_count, 0) AS retry_count
            """; // __CHANNELS__ is replaced with the channel IN-list placeholders (each cast ?::channel)

    private static final String MARK_SENT_SQL =
            """
            UPDATE reach_task
            SET status = 'SENT'::reach_task_status,
                sent_at = ?,
                last_attempt_at = ?,
                locked_by = NULL,
                locked_until = NULL,
                last_error = NULL
            WHERE id = ? AND status = 'PROCESSING'::reach_task_status
            """;

    private static final String INSERT_SEND_RESULT_SQL =
            """
            INSERT INTO send_result (id, reach_task_id, provider_message_id, outcome, occurred_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (provider_message_id) WHERE provider_message_id IS NOT NULL DO NOTHING
            """;

    private static final String SCHEDULE_RETRY_SQL =
            """
            UPDATE reach_task
            SET status = 'RETRY_SCHEDULED'::reach_task_status,
                retry_count = COALESCE(retry_count, 0) + 1,
                next_retry_at = ?,
                last_attempt_at = ?,
                last_error = ?,
                locked_by = NULL,
                locked_until = NULL
            WHERE id = ? AND status = 'PROCESSING'::reach_task_status
            """;

    private static final String MARK_FAILED_SQL =
            """
            UPDATE reach_task
            SET status = 'FAILED'::reach_task_status,
                last_attempt_at = ?,
                last_error = ?,
                next_retry_at = NULL,
                locked_by = NULL,
                locked_until = NULL
            WHERE id = ? AND status = 'PROCESSING'::reach_task_status
            """;

    private static final String MARK_DEAD_LETTERED_SQL =
            """
            UPDATE reach_task
            SET status = 'DLQ'::reach_task_status,
                last_attempt_at = ?,
                last_error = ?,
                next_retry_at = NULL,
                locked_by = NULL,
                locked_until = NULL
            WHERE id = ? AND status = 'PROCESSING'::reach_task_status
            """;

    private static final String REAP_EXPIRED_LEASES_SQL =
            """
            UPDATE reach_task
            SET status = 'PENDING'::reach_task_status,
                locked_by = NULL,
                locked_until = NULL
            WHERE status = 'PROCESSING'::reach_task_status
              AND locked_until < ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ReachPlanTemplateExtractor templateExtractor;

    /**
     * @param jdbcTemplate auto-configured template for the claim and write-back SQL
     * @param transactionManager backs the per-stage short transactions
     * @param templateExtractor reads {@code templateRef} from the joined frozen reach plan
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring injects the singleton JdbcTemplate / transaction manager by reference; "
                    + "storing these framework-managed beans is the intended DI wiring, not a mutable-state leak.")
    public ReachTaskDispatchDao(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ReachPlanTemplateExtractor templateExtractor) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.templateExtractor = templateExtractor;
    }

    /**
     * Stage one (short transaction): claim and mark a batch PROCESSING under {@code FOR UPDATE SKIP
     * LOCKED}, returning the claimed tasks. The transaction commits on return, releasing the locks and
     * the connection before any external send happens.
     *
     * @param workerId the lease owner written to {@code locked_by}
     * @param eligibleChannels the channels whose adapter is currently available (breaker not OPEN) —
     *     only tasks on these channels are claimed, so a degraded channel's tasks stay PENDING
     * @param batchSize maximum rows to claim
     * @param leaseDuration how long the {@code locked_until} lease runs from {@code now}
     * @param now the single dispatch-time instant (claim eligibility + lease basis)
     * @return the claimed tasks (possibly empty; empty when no channels are eligible)
     */
    public List<ClaimedTask> claimBatch(
            String workerId, List<Channel> eligibleChannels, int batchSize, Duration leaseDuration, Instant now) {
        if (eligibleChannels.isEmpty()) {
            return List.of();
        }
        Timestamp nowTs = Timestamp.from(now);
        Timestamp leaseUntil = Timestamp.from(now.plus(leaseDuration));
        String channelPlaceholders = String.join(",", Collections.nCopies(eligibleChannels.size(), "?::channel"));
        String sql = CLAIM_SQL_TEMPLATE.replace("__CHANNELS__", channelPlaceholders);
        return transactionTemplate.execute(status -> jdbcTemplate.query(
                sql,
                ps -> {
                    int idx = 1;
                    ps.setTimestamp(idx++, nowTs); // next_retry_at <= now
                    for (Channel channel : eligibleChannels) {
                        ps.setString(idx++, channel.name()); // channel IN (...) — cast ?::channel in SQL
                    }
                    ps.setInt(idx++, batchSize); // LIMIT
                    ps.setString(idx++, workerId); // locked_by
                    ps.setTimestamp(idx++, leaseUntil); // locked_until
                    ps.setTimestamp(idx, nowTs); // processing_started_at
                },
                (rs, rowNum) -> new ClaimedTask(
                        rs.getObject("id", UUID.class),
                        rs.getObject("campaign_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        Channel.valueOf(rs.getString("channel")),
                        rs.getString("send_cycle_key"),
                        templateExtractor.extract(rs.getString("reach_plan")),
                        rs.getInt("retry_count"))));
    }

    /**
     * Stage two (short transaction): record the successful send. Transitions PROCESSING → SENT, clears
     * the lease, and writes one {@code send_result} row (PII-minimized: provider message id + outcome
     * only). The {@code provider_message_id} unique index makes a redelivered duplicate result a no-op.
     *
     * @param taskId the claimed task id
     * @param providerMessageId the provider's accepted-send id (PII-minimized dedup handle)
     * @param now the write-back instant
     */
    public void markSent(UUID taskId, String providerMessageId, Instant now) {
        Timestamp nowTs = Timestamp.from(now);
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(MARK_SENT_SQL, nowTs, nowTs, taskId);
            jdbcTemplate.update(INSERT_SEND_RESULT_SQL, UUID.randomUUID(), taskId, providerMessageId, "SENT", nowTs);
        });
    }

    /**
     * Stage two (short transaction): a retryable failure. Transitions PROCESSING → RETRY_SCHEDULED,
     * increments {@code retry_count}, sets the exponential-backoff {@code next_retry_at}, records the
     * error, and clears the lease — so the task is never stuck in PROCESSING.
     *
     * @param taskId the claimed task id
     * @param nextRetryAt the backed-off time the task becomes eligible again
     * @param error a short error description stored in {@code last_error}
     * @param now the write-back instant
     */
    public void scheduleRetry(UUID taskId, Instant nextRetryAt, String error, Instant now) {
        Timestamp nowTs = Timestamp.from(now);
        transactionTemplate.executeWithoutResult(
                status -> jdbcTemplate.update(SCHEDULE_RETRY_SQL, Timestamp.from(nextRetryAt), nowTs, error, taskId));
    }

    /**
     * Stage two (short transaction): a terminal failure for a <em>non-retryable</em> reason (suppression
     * hit, or a permanent provider failure such as an invalid address). Transitions PROCESSING → FAILED,
     * records the error, and clears the lease.
     *
     * @param taskId the claimed task id
     * @param error a short error description stored in {@code last_error}
     * @param now the write-back instant
     */
    public void markFailed(UUID taskId, String error, Instant now) {
        Timestamp nowTs = Timestamp.from(now);
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(MARK_FAILED_SQL, nowTs, error, taskId));
    }

    /**
     * Stage two (short transaction): dead-letter an exhausted task (task 9.2, spec「失敗保留與 DLQ」).
     * Transitions PROCESSING → DLQ, records the last error, and clears the lease — so the task is
     * durably retained for manual inspection / replay rather than silently lost.
     *
     * <p>The transition is the single guarded step PROCESSING → DLQ (not PROCESSING → FAILED → DLQ): the
     * MVP lifecycle subset is {@code PENDING/PROCESSING/SENT/FAILED/DLQ} and the row is only ever in
     * PROCESSING when this runs, so one atomic guarded {@code WHERE status='PROCESSING'} update is the
     * simplest correct realization of the diagram's path into DLQ without introducing an illegal edge or
     * an intermediate-state write that a concurrent reader could observe. The dispatcher publishes the
     * {@code reach.dlq} event <em>before</em> calling this (publish-then-mark, see {@code
     * ReachTaskDispatcher}), so a crash between publish and mark leaves the row PROCESSING with an
     * expired lease for the Reaper (task 9.3) — never silently lost.
     *
     * @param taskId the claimed task id
     * @param error a short error description stored in {@code last_error}
     * @param now the write-back instant
     */
    public void markDeadLettered(UUID taskId, String error, Instant now) {
        Timestamp nowTs = Timestamp.from(now);
        transactionTemplate.executeWithoutResult(
                status -> jdbcTemplate.update(MARK_DEAD_LETTERED_SQL, nowTs, error, taskId));
    }

    /**
     * Reaper reset (task 9.3, short transaction): reclaim tasks stuck in PROCESSING whose lease has
     * expired — the symptom of a worker that claimed a task (stage one) then crashed before writing the
     * outcome back (stage two). The single guarded {@code UPDATE … WHERE status='PROCESSING' AND
     * locked_until < ?} resets each such row to PENDING and clears the lease, so the dispatcher's next
     * poll re-claims it (a PENDING row with an unchanged/elapsed {@code next_retry_at} satisfies the
     * stage-one claim predicate {@code next_retry_at IS NULL OR <= now}). {@code processing_started_at}
     * is intentionally left untouched as an audit trace of the prior attempt.
     *
     * <p>The guard makes the reset idempotent and safe under concurrent reapers: a row already reset to
     * PENDING no longer matches, and two reapers resetting the same row is harmless — so no distributed
     * lock is required.
     *
     * @param now the single reap-tick instant; rows whose lease expired strictly before this are reset
     * @return the number of rows reset to PENDING (0 when nothing is stuck)
     */
    public int reapExpiredLeases(Instant now) {
        Timestamp nowTs = Timestamp.from(now);
        Integer reaped = transactionTemplate.execute(status -> jdbcTemplate.update(REAP_EXPIRED_LEASES_SQL, nowTs));
        return reaped == null ? 0 : reaped;
    }
}
