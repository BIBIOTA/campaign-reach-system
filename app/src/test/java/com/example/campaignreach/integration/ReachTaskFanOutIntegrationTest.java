package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.campaignreach.reach.audience.AudienceResolver;
import com.example.campaignreach.reach.audience.Recipient;
import com.example.campaignreach.reach.audience.TargetSpec;
import com.example.campaignreach.reach.audience.TargetSpecParser;
import com.example.campaignreach.reach.orchestrator.ExpansionProperties;
import com.example.campaignreach.reach.orchestrator.PagedAudienceExpander;
import com.example.campaignreach.reach.orchestrator.ReachPlanChannelExtractor;
import com.example.campaignreach.reach.orchestrator.ReachRequest;
import com.example.campaignreach.reach.orchestrator.ReachRequestRepository;
import com.example.campaignreach.shared.event.TriggerType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Persistence-level integration test for paged {@code reach_task} fan-out (task 7.3), backed by a real
 * PostgreSQL container with the Flyway V6 schema applied. Auto-skipped without Docker via the inherited
 * {@link RequiresDocker} condition.
 *
 * <p>Verifies the spec §5 scenarios against real SQL (not mocks): the ON CONFLICT DO NOTHING dedup on
 * the four-column unique key, crash-resume convergence to exactly N rows, frequency-cap skipping of a
 * recent different-cycle user, and the reach_request status advance + total_count backfill.
 *
 * <p>The {@link AudienceResolver} is mocked so the recipient set is controlled precisely; everything
 * else (parser, channel extractor, JdbcTemplate, transaction manager) is the real wiring against the
 * container DB.
 */
class ReachTaskFanOutIntegrationTest extends AbstractIntegrationTest {

    private static final String TARGET_SPEC = "{\"kind\":\"CONDITION\",\"conditions\":{\"region\":\"TW\"}}";
    private static final String REACH_PLAN =
            "{\"channel\":\"EMAIL\",\"templateRef\":\"welcome\",\"timing\":\"SCHEDULED\"}";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TargetSpecParser targetSpecParser;

    @Autowired
    private ReachPlanChannelExtractor reachPlanChannelExtractor;

    @Autowired
    private ReachRequestRepository reachRequestRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM reach_task");
        jdbcTemplate.update("DELETE FROM reach_request");
    }

    private PagedAudienceExpander expanderFor(AudienceResolver resolver) {
        return new PagedAudienceExpander(
                targetSpecParser,
                reachPlanChannelExtractor,
                resolver,
                jdbcTemplate,
                transactionManager,
                new ExpansionProperties(
                        new ExpansionProperties.Expansion(3), // small pages so multi-page paths are exercised
                        new ExpansionProperties.FrequencyCap(Duration.ofHours(24))));
    }

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

    private List<Recipient> recipients(List<UUID> userIds) {
        return userIds.stream().map(Recipient::new).toList();
    }

    private int taskCount(UUID reachRequestId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reach_task WHERE reach_request_id = ?", Integer.class, reachRequestId);
        return count == null ? 0 : count;
    }

    /** Scenario: 一筆請求展開成 N 筆任務 — N recipients become N PENDING rows, status ends DISPATCHING, total_count=N. */
    @Test
    void recipientsExpandIntoTasksAndRequestEndsDispatchingWithTotalCount() {
        UUID campaignId = UUID.randomUUID();
        ReachRequest request = landedRequest(campaignId, "cycle-A");
        List<UUID> users =
                IntStream.range(0, 7).mapToObj(i -> UUID.randomUUID()).toList();

        AudienceResolver resolver = mock(AudienceResolver.class);
        when(resolver.resolve(ArgumentMatchers.any(TargetSpec.class))).thenReturn(recipients(users));

        expanderFor(resolver).expand(request);

        assertThat(taskCount(request.getId())).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status::text FROM reach_request WHERE id = ?", String.class, request.getId()))
                .isEqualTo("DISPATCHING");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT total_count FROM reach_request WHERE id = ?", Integer.class, request.getId()))
                .isEqualTo(7);
    }

    /** Scenario: 斷點續跑 + 同一人同週期只建立一筆 — re-running expand keeps exactly N (ON CONFLICT dedups same cycle). */
    @Test
    void rerunningExpandConvergesToExactlyTheSameRowsViaOnConflict() {
        UUID campaignId = UUID.randomUUID();
        ReachRequest request = landedRequest(campaignId, "cycle-B");
        List<UUID> users =
                IntStream.range(0, 5).mapToObj(i -> UUID.randomUUID()).toList();

        AudienceResolver resolver = mock(AudienceResolver.class);
        when(resolver.resolve(ArgumentMatchers.any(TargetSpec.class))).thenReturn(recipients(users));

        PagedAudienceExpander expander = expanderFor(resolver);
        expander.expand(request);
        // Redelivery: same event, same recipients. ON CONFLICT DO NOTHING must not double-create.
        expander.expand(request);

        assertThat(taskCount(request.getId())).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT total_count FROM reach_request WHERE id = ?", Integer.class, request.getId()))
                .isEqualTo(5);
    }

    /** Scenario: 短時間頻控跳過 — a user with a recent task in a DIFFERENT cycle is skipped by the freq cap. */
    @Test
    void userWithRecentDifferentCycleTaskIsSkippedByFrequencyCap() {
        UUID campaignId = UUID.randomUUID();
        UUID cappedUser = UUID.randomUUID();
        UUID freshUser = UUID.randomUUID();

        // Seed a historical task for cappedUser in a DIFFERENT send cycle, created just now (within window).
        jdbcTemplate.update(
                """
                INSERT INTO reach_task (id, reach_request_id, campaign_id, user_id, send_cycle_key, channel, status, created_at)
                VALUES (?, ?, ?, ?, 'cycle-PRIOR', 'EMAIL'::channel, 'PENDING'::reach_task_status, ?)
                """,
                UUID.randomUUID(),
                landedRequest(campaignId, "cycle-PRIOR").getId(),
                campaignId,
                cappedUser,
                Timestamp.from(Instant.now()));

        ReachRequest request = landedRequest(campaignId, "cycle-NOW");
        AudienceResolver resolver = mock(AudienceResolver.class);
        when(resolver.resolve(ArgumentMatchers.any(TargetSpec.class)))
                .thenReturn(recipients(List.of(cappedUser, freshUser)));

        expanderFor(resolver).expand(request);

        // Only the fresh user is inserted for this request; the capped user is skipped (frequency cap).
        assertThat(taskCount(request.getId())).isEqualTo(1);
        Integer cappedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reach_task WHERE reach_request_id = ? AND user_id = ?",
                Integer.class,
                request.getId(),
                cappedUser);
        assertThat(cappedRows).isZero();
    }
}
