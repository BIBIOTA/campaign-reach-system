package com.example.campaignreach.reach.dispatcher;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies the configurable data-retention policy to the reach audit trail (task 11.1, spec「收件人 PII
 * 最小化與抑制名單」scenario「觸達稽核軌跡屆期歸檔或刪除」, NFR-005). Each purge pass deletes {@code reach_task}
 * (and its child {@code send_result}) rows whose audit trail has aged past the configured retention
 * period. Deletion is the MVP discharge of design.md §10「歸檔或刪除」.
 *
 * <p><strong>FK-ordered delete.</strong> {@code send_result.reach_task_id} has a foreign key to {@code
 * reach_task(id)}, so children are deleted <em>first</em> (those belonging to to-be-purged tasks),
 * then the parent tasks — otherwise the parent delete would violate the FK. The cutoff is computed
 * once per pass as {@code now - retentionPeriod} and reused for both statements so they target a
 * consistent set.
 *
 * <p><strong>Bounded batches.</strong> A single sweep can span a very large expired set (100k+ rows on
 * first run or after a long backlog). Deleting it all in one statement/transaction would hold long row
 * locks, bloat the WAL, and keep a long-running transaction open (blocking autovacuum). Instead the
 * pass loops, each iteration deleting at most {@link RetentionProperties#purgeBatchSize()} terminal
 * tasks (oldest first) <em>in its own short transaction</em>, until a batch comes back short — so each
 * transaction stays small regardless of how much has aged out. Child and parent sub-selects share the
 * same {@code ORDER BY created_at, id LIMIT n} predicate against the (unchanged-between-statements)
 * {@code reach_task} table, so both target the identical batch.
 *
 * <p><strong>Terminal-state safety guard.</strong> Only tasks in a terminal status (SENT / FAILED /
 * DLQ / CANCELLED) are eligible for purge. In-flight work (PENDING / PROCESSING / RETRY_SCHEDULED) is
 * never deleted no matter how old, so a long-stuck-but-still-live task is never silently lost to the
 * retention sweep — retention only reaps finished audit rows.
 *
 * <p><strong>No ShedLock.</strong> Like {@link ReachTaskReaper}, the purge is an idempotent guarded
 * DELETE: a row already deleted no longer matches the {@code created_at < cutoff AND status terminal}
 * predicate, so two purgers (or a purger racing the dispatcher) are harmless — a concurrent purger that
 * loses the race simply deletes fewer rows and the residual is reaped by the next sweep. No distributed
 * lock is wired.
 */
@Component
public class ReachAuditTrailPurger {

    // Delete send_result children of the current batch of to-be-purged tasks first (FK
    // send_result.reach_task_id -> reach_task.id). A task is purgeable when its created_at is older than
    // the cutoff AND it is in a terminal status — in-flight tasks are excluded so live work is never
    // deleted. The batch is the oldest `?` such tasks (ORDER BY created_at, id LIMIT ?), keeping each
    // transaction bounded; the parent delete below re-selects the identical batch.
    private static final String DELETE_SEND_RESULTS_SQL =
            """
            DELETE FROM send_result
            WHERE reach_task_id IN (
                SELECT id FROM reach_task
                WHERE created_at < ?
                  AND status IN (
                      'SENT'::reach_task_status,
                      'FAILED'::reach_task_status,
                      'DLQ'::reach_task_status,
                      'CANCELLED'::reach_task_status
                  )
                ORDER BY created_at, id
                LIMIT ?
            )
            """;

    private static final String DELETE_TASKS_SQL =
            """
            DELETE FROM reach_task
            WHERE id IN (
                SELECT id FROM reach_task
                WHERE created_at < ?
                  AND status IN (
                      'SENT'::reach_task_status,
                      'FAILED'::reach_task_status,
                      'DLQ'::reach_task_status,
                      'CANCELLED'::reach_task_status
                  )
                ORDER BY created_at, id
                LIMIT ?
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final RetentionProperties retentionProperties;

    /**
     * @param jdbcTemplate auto-configured template for the guarded purge deletes
     * @param transactionManager backs the short purge transaction (children then parents in one tx)
     * @param retentionProperties the validated, never-permanent retention period
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring injects the singleton JdbcTemplate / transaction manager by reference; "
                    + "storing these framework-managed beans is the intended DI wiring, not a mutable-state leak.")
    public ReachAuditTrailPurger(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            RetentionProperties retentionProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.retentionProperties = retentionProperties;
    }

    /**
     * One purge pass: delete terminal-state {@code reach_task} rows (and their {@code send_result}
     * children) created before {@code now - retentionPeriod}. The pass loops in bounded batches (see the
     * class doc) until a batch reaps fewer than {@code purgeBatchSize} tasks, so each transaction stays
     * short no matter how much has aged out.
     *
     * @param now the pass instant; the cutoff is {@code now - retentionPeriod}
     * @return the per-pass totals — {@code reach_task} rows and {@code send_result} children purged
     */
    public PurgeResult purgeExpired(Instant now) {
        Instant cutoff = now.minus(retentionProperties.period());
        Timestamp cutoffTs = Timestamp.from(cutoff);
        int batchSize = retentionProperties.purgeBatchSize();
        int totalTasks = 0;
        int totalSendResults = 0;
        int batchTasks;
        do {
            PurgeResult batch = purgeBatch(cutoffTs, batchSize);
            batchTasks = batch.tasks();
            totalTasks += batch.tasks();
            totalSendResults += batch.sendResults();
            // A full batch means more may remain; a short batch means the expired set is drained.
        } while (batchTasks == batchSize);
        return new PurgeResult(totalTasks, totalSendResults);
    }

    /** Deletes one bounded batch (children then parents) in its own short transaction. */
    private PurgeResult purgeBatch(Timestamp cutoffTs, int batchSize) {
        PurgeResult result = transactionTemplate.execute(status -> {
            int sendResults = jdbcTemplate.update(DELETE_SEND_RESULTS_SQL, cutoffTs, batchSize);
            int tasks = jdbcTemplate.update(DELETE_TASKS_SQL, cutoffTs, batchSize);
            return new PurgeResult(tasks, sendResults);
        });
        return result == null ? PurgeResult.EMPTY : result;
    }

    /**
     * Totals from a purge pass (or a single batch).
     *
     * @param tasks number of {@code reach_task} rows deleted
     * @param sendResults number of {@code send_result} child rows deleted
     */
    public record PurgeResult(int tasks, int sendResults) {
        static final PurgeResult EMPTY = new PurgeResult(0, 0);
    }
}
