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
 * <p><strong>Terminal-state safety guard.</strong> Only tasks in a terminal status (SENT / FAILED /
 * DLQ / CANCELLED) are eligible for purge. In-flight work (PENDING / PROCESSING / RETRY_SCHEDULED) is
 * never deleted no matter how old, so a long-stuck-but-still-live task is never silently lost to the
 * retention sweep — retention only reaps finished audit rows.
 *
 * <p><strong>No ShedLock.</strong> Like {@link ReachTaskReaper}, the purge is an idempotent guarded
 * DELETE: a row already deleted no longer matches the {@code created_at < cutoff AND status terminal}
 * predicate, so two purgers (or a purger racing the dispatcher) are harmless. No distributed lock is
 * wired.
 */
@Component
public class ReachAuditTrailPurger {

    // Delete send_result children of to-be-purged tasks first (FK send_result.reach_task_id ->
    // reach_task.id). A task is purgeable when its created_at is older than the cutoff AND it is in a
    // terminal status — in-flight tasks are excluded so live work is never deleted.
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
            )
            """;

    private static final String DELETE_TASKS_SQL =
            """
            DELETE FROM reach_task
            WHERE created_at < ?
              AND status IN (
                  'SENT'::reach_task_status,
                  'FAILED'::reach_task_status,
                  'DLQ'::reach_task_status,
                  'CANCELLED'::reach_task_status
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
     * children) created before {@code now - retentionPeriod}. Children are deleted before parents to
     * respect the FK, both inside one short transaction.
     *
     * @param now the pass instant; the cutoff is {@code now - retentionPeriod}
     * @return number of {@code reach_task} rows purged
     */
    public int purgeExpired(Instant now) {
        Instant cutoff = now.minus(retentionProperties.period());
        Timestamp cutoffTs = Timestamp.from(cutoff);
        Integer purged = transactionTemplate.execute(status -> {
            jdbcTemplate.update(DELETE_SEND_RESULTS_SQL, cutoffTs);
            return jdbcTemplate.update(DELETE_TASKS_SQL, cutoffTs);
        });
        return purged == null ? 0 : purged;
    }
}
