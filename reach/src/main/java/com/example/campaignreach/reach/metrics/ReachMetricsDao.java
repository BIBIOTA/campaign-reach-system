package com.example.campaignreach.reach.metrics;

import com.example.campaignreach.reach.orchestrator.ReachTaskStatus;
import com.example.campaignreach.shared.event.Channel;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only persistence for the reach-metrics query slice (task 10.3, FR-018, NFR-005).
 *
 * <p>Follows the established raw-{@link JdbcTemplate} + static-SQL-constant + {@code ?::enum}-cast
 * pattern of {@code ReachTaskDispatchDao} / {@code ReachRequestCountAggregator}. All inputs
 * ({@code campaignId}, {@code userId}) are bound as positional parameters — never interpolated.
 *
 * <p>The campaign-level distribution is computed from {@code reach_task} grouped by status (the
 * {@code idx_reach_task_campaign_status} index backs it): {@code reach_request} only folds task
 * states into pending/sent/failed buckets, so the full seven-way {@link ReachTaskStatus}
 * distribution must come from the task table. The per-recipient lookup reads the small set of a
 * single user's tasks for one campaign (backed by the {@code (user_id, campaign_id)} index).
 */
@Component
public class ReachMetricsDao {

    private static final String STATUS_DISTRIBUTION_SQL =
            """
            SELECT status::text AS status, COUNT(*)::bigint AS recipient_count
            FROM reach_task
            WHERE campaign_id = ?
            GROUP BY status
            """;

    private static final String RECIPIENT_STATUS_SQL =
            """
            SELECT campaign_id, user_id, channel::text AS channel, send_cycle_key, status::text AS status
            FROM reach_task
            WHERE campaign_id = ? AND user_id = ?
            ORDER BY send_cycle_key, channel
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param jdbcTemplate auto-configured template over the app DataSource for the read-only queries
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring injects the singleton JdbcTemplate by reference; storing this "
                    + "framework-managed bean is the intended DI wiring, not a mutable-state leak.")
    public ReachMetricsDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Counts the campaign's reach tasks grouped by lifecycle status, across all of its batches. Only
     * statuses that actually occur are returned; absent statuses are folded to {@code 0} by the
     * caller. A campaign with no tasks yields an empty map.
     *
     * @param campaignId the campaign to aggregate
     * @return recipient count per {@link ReachTaskStatus} that occurs for the campaign
     */
    public Map<ReachTaskStatus, Long> countTasksByStatus(UUID campaignId) {
        Map<ReachTaskStatus, Long> counts = new EnumMap<>(ReachTaskStatus.class);
        jdbcTemplate.query(
                STATUS_DISTRIBUTION_SQL,
                rs -> {
                    counts.put(ReachTaskStatus.valueOf(rs.getString("status")), rs.getLong("recipient_count"));
                },
                campaignId);
        return counts;
    }

    /**
     * Looks up one user's reach task status(es) within a campaign — possibly several (one per send
     * cycle / channel). PII-minimized: selects only identifiers and status, never email or content.
     *
     * @param campaignId the campaign to scope the lookup to
     * @param userId the recipient user
     * @return the recipient's status entries (empty when the user has no task for the campaign)
     */
    public List<RecipientReachStatus> findRecipientStatuses(UUID campaignId, UUID userId) {
        List<RecipientReachStatus> rows = new ArrayList<>();
        jdbcTemplate.query(
                RECIPIENT_STATUS_SQL,
                rs -> {
                    rows.add(new RecipientReachStatus(
                            rs.getObject("campaign_id", UUID.class),
                            rs.getObject("user_id", UUID.class),
                            Channel.valueOf(rs.getString("channel")),
                            rs.getString("send_cycle_key"),
                            ReachTaskStatus.valueOf(rs.getString("status"))));
                },
                campaignId,
                userId);
        return rows;
    }
}
