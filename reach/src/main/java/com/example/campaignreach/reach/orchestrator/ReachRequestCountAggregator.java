package com.example.campaignreach.reach.orchestrator;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Maintains the near-real-time {@code reach_request} count projection from {@code reach_task} rows
 * (task 10.2, FR-018, NFR-002). The dispatcher writes only task state transitions; this aggregator
 * periodically folds active-batch task states back into the activity-level batch counters, avoiding a
 * hot row-lock on the same {@code reach_request} row for every individual send result.
 */
@Component
public class ReachRequestCountAggregator {

    private static final String AGGREGATE_COUNTS_SQL =
            """
            WITH active_requests AS (
                SELECT id
                FROM reach_request
                WHERE status IN ('EXPANDING'::reach_request_status, 'DISPATCHING'::reach_request_status)
            ),
            aggregated AS (
                SELECT active_requests.id,
                    COUNT(t.id) FILTER (
                        WHERE t.status IN (
                            'PENDING'::reach_task_status,
                            'PROCESSING'::reach_task_status,
                            'RETRY_SCHEDULED'::reach_task_status
                        )
                    )::int AS pending_count,
                    COUNT(t.id) FILTER (WHERE t.status = 'SENT'::reach_task_status)::int AS sent_count,
                    COUNT(t.id) FILTER (
                        WHERE t.status IN ('FAILED'::reach_task_status, 'DLQ'::reach_task_status)
                    )::int AS failed_count
                FROM active_requests
                LEFT JOIN reach_task t ON t.reach_request_id = active_requests.id
                GROUP BY active_requests.id
            )
            UPDATE reach_request r
            SET pending_count = aggregated.pending_count,
                sent_count = aggregated.sent_count,
                failed_count = aggregated.failed_count
            FROM aggregated
            WHERE r.id = aggregated.id
              AND (
                  r.pending_count IS DISTINCT FROM aggregated.pending_count
                  OR r.sent_count IS DISTINCT FROM aggregated.sent_count
                  OR r.failed_count IS DISTINCT FROM aggregated.failed_count
              )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param jdbcTemplate auto-configured template for the set-based projection update
     * @param transactionManager backs the short aggregation transaction
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring injects the singleton JdbcTemplate / transaction manager by reference; "
                    + "storing these framework-managed beans is the intended DI wiring, not a mutable-state leak.")
    public ReachRequestCountAggregator(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Rebuilds {@code pending_count}, {@code sent_count}, and {@code failed_count} for active
     * EXPANDING/DISPATCHING batches from the current task statuses. This intentionally does not
     * maintain counters inline in
     * {@code markSent}/{@code markFailed}/{@code scheduleRetry}: seconds-level report lag is accepted
     * so high-volume task transitions do not serialize on one batch row.
     *
     * @return number of {@code reach_request} rows whose counter values changed
     */
    public int aggregateCounts() {
        Integer updated = transactionTemplate.execute(status -> jdbcTemplate.update(AGGREGATE_COUNTS_SQL));
        return updated == null ? 0 : updated;
    }
}
