package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campaignreach.reach.dispatcher.ReachAuditTrailPurger;
import com.example.campaignreach.reach.dispatcher.RetentionProperties;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Docker-gated persistence test for the PII-minimization + data-retention slice (task 11.1, spec
 * 「收件人 PII 最小化與抑制名單」, NFR-005), backed by a real PostgreSQL container with the Flyway-owned V6
 * (reach_task) + V8 (send_result) schema applied. Auto-skipped without Docker via the inherited {@link
 * RequiresDocker} condition.
 *
 * <p>Covers two scenarios:
 * <ul>
 *   <li><strong>不落收件 PII</strong> — asserts at the catalog level that neither {@code reach_task} nor
 *       {@code send_result} carries any recipient email / message-content column; {@code reach_task}
 *       keeps only {@code user_id}, and {@code send_result} keeps only {@code provider_message_id} +
 *       {@code outcome} (plus id / fk / time). The actual email is resolved at send time, never
 *       persisted.</li>
 *   <li><strong>觸達稽核軌跡屆期歸檔或刪除</strong> — drives the real FK-ordered DELETE through {@link
 *       ReachAuditTrailPurger}: an aged terminal task and its send_result child are removed without an
 *       FK violation, while a fresh row and an aged-but-in-flight row are retained.</li>
 * </ul>
 */
class ReachAuditRetentionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM send_result");
        jdbcTemplate.update("DELETE FROM reach_task");
        jdbcTemplate.update("DELETE FROM reach_request");
        jdbcTemplate.update("DELETE FROM campaign");
    }

    /**
     * Scenario: 不落收件 PII — the audit tables carry no recipient PII column. reach_task stores only
     * user_id (a UUID, not an email), and send_result stores only provider_message_id + outcome — never
     * the recipient address or message content.
     */
    @Test
    void 不落收件PII() {
        List<String> reachTaskCols = columnsOf("reach_task");
        assertThat(reachTaskCols).contains("user_id");
        // No recipient PII / message content columns on the per-recipient task row.
        assertThat(reachTaskCols).noneSatisfy(c -> assertThat(c).contains("email"));
        assertThat(reachTaskCols).doesNotContain("recipient", "address", "to_address", "content", "body", "subject");

        List<String> sendResultCols = columnsOf("send_result");
        // send_result keeps only the provider handle + coarse outcome (plus id, fk, time).
        assertThat(sendResultCols)
                .containsExactlyInAnyOrder("id", "reach_task_id", "provider_message_id", "outcome", "occurred_at");
        assertThat(sendResultCols).noneSatisfy(c -> assertThat(c).contains("email"));
        assertThat(sendResultCols).doesNotContain("recipient", "address", "content", "body", "subject");
    }

    /**
     * Scenario: 觸達稽核軌跡屆期歸檔或刪除 — rows older than the cutoff and in a terminal state are deleted,
     * their send_result children are deleted first so the FK is not violated, and fresh / in-flight rows
     * are retained.
     */
    @Test
    void 觸達稽核軌跡屆期歸檔或刪除() {
        UUID campaignId = seedCampaign();
        UUID requestId = seedRequest(campaignId);
        Instant now = Instant.now();
        Duration retention = Duration.ofDays(30);

        // (1) Aged + terminal (SENT) with a send_result child → must be purged (child first, no FK violation).
        UUID agedTerminal = seedTask(requestId, campaignId, now.minus(Duration.ofDays(60)), "SENT");
        seedSendResult(agedTerminal, now.minus(Duration.ofDays(60)));
        // (2) Aged but in-flight (PENDING) → retained (terminal-state safety guard).
        UUID agedInFlight = seedTask(requestId, campaignId, now.minus(Duration.ofDays(60)), "PENDING");
        // (3) Fresh + terminal (FAILED) → retained (within retention window).
        UUID freshTerminal = seedTask(requestId, campaignId, now.minus(Duration.ofDays(1)), "FAILED");

        ReachAuditTrailPurger purger =
                new ReachAuditTrailPurger(jdbcTemplate, transactionManager, new RetentionProperties(retention));

        int purged = purger.purgeExpired(now);

        assertThat(purged).isEqualTo(1);
        assertThat(taskExists(agedTerminal)).isFalse();
        assertThat(sendResultCountFor(agedTerminal)).isZero(); // child deleted with parent, no FK violation
        assertThat(taskExists(agedInFlight)).isTrue(); // in-flight never purged
        assertThat(taskExists(freshTerminal)).isTrue(); // within retention window
    }

    private List<String> columnsOf(String table) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = ? AND table_schema = 'public'",
                String.class,
                table);
    }

    private boolean taskExists(UUID taskId) {
        Integer count =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reach_task WHERE id = ?", Integer.class, taskId);
        return count != null && count > 0;
    }

    private int sendResultCountFor(UUID taskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM send_result WHERE reach_task_id = ?", Integer.class, taskId);
        return count == null ? 0 : count;
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

    private UUID seedTask(UUID requestId, UUID campaignId, Instant createdAt, String status) {
        UUID taskId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reach_task
                    (id, reach_request_id, campaign_id, user_id, send_cycle_key, channel, status,
                     retry_count, created_at)
                VALUES (?, ?, ?, ?, ?, 'EMAIL'::channel, ?::reach_task_status, 0, ?)
                """,
                taskId,
                requestId,
                campaignId,
                UUID.randomUUID(),
                "cycle-" + taskId,
                status,
                Timestamp.from(createdAt));
        return taskId;
    }

    private void seedSendResult(UUID taskId, Instant occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO send_result (id, reach_task_id, provider_message_id, outcome, occurred_at)
                VALUES (?, ?, ?, 'SENT', ?)
                """,
                UUID.randomUUID(),
                taskId,
                "provider-" + taskId,
                Timestamp.from(occurredAt));
    }
}
