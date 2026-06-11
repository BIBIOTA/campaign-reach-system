package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.campaignreach.campaign.domain.CurrentOperator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end guard for the cancel-on-deactivation chain (task 10.1, FR-017, US-002), booting the full
 * Spring context on a real PostgreSQL container. It pins the wiring the unit/DAO tests cannot see:
 *
 * <ul>
 *   <li>{@code CampaignStatusChangeListenerTest} mocks the DAO, so it never proves the listener is
 *       actually invoked by a real status transition.
 *   <li>{@code ReachTaskDispatchDaoIntegrationTest} calls {@code cancelPendingForCampaign} directly,
 *       so it never proves the publish → {@code @EventListener} → cancel chain fires.
 * </ul>
 *
 * <p>This test drives a real operator HTTP transition (RUNNING → PAUSED) and asserts that the
 * campaign's not-yet-sent {@code reach_task} rows are CANCELLED <em>atomically</em> with the status
 * change, while an already-PROCESSING task is left to finish (the bounded, accepted leak). That
 * atomicity depends on {@code CampaignStatusChangeListener} staying a synchronous, same-transaction
 * {@code @EventListener}; this test is the regression fence for that contract.
 *
 * <p>Auto-skipped without Docker via the inherited {@link RequiresDocker} condition.
 */
@AutoConfigureMockMvc
class CampaignDeactivationCancelsReachTasksIntegrationTest extends AbstractIntegrationTest {

    /** Matches the throwaway operator wired in {@link AbstractIntegrationTest#infrastructureProperties}. */
    private static final String OPERATOR_USER = "it-operator";

    private static final String OPERATOR_PASS = "it-operator-secret";
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(86_400);
    private static final String REACH_PLAN =
            "{\"channel\":\"EMAIL\",\"templateRef\":\"welcome\",\"timing\":\"SCHEDULED\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM reach_task");
        jdbcTemplate.update("DELETE FROM reach_request");
        jdbcTemplate.update("DELETE FROM campaign");
        CurrentOperator.clear();
    }

    /**
     * Scenario (10.1): pausing a RUNNING campaign over HTTP cancels its PENDING / RETRY_SCHEDULED reach
     * tasks (via the published {@code CampaignStatusChanged} → reach listener, same transaction) while
     * an already-PROCESSING task is untouched and allowed to complete.
     */
    @Test
    void pausingRunningCampaignViaHttpCancelsNotYetSentTasksButLeavesProcessing() throws Exception {
        UUID campaignId = createDraftCampaign();
        int version = transition(campaignId, "SCHEDULED", 0);
        version = transition(campaignId, "RUNNING", version);

        UUID requestId = seedRequest(campaignId);
        UUID pending = seedTask(requestId, campaignId, "PENDING");
        UUID retryScheduled = seedTask(requestId, campaignId, "RETRY_SCHEDULED");
        UUID processing = seedTask(requestId, campaignId, "PROCESSING");

        transition(campaignId, "PAUSED", version);

        assertThat(taskStatus(pending)).isEqualTo("CANCELLED");
        assertThat(taskStatus(retryScheduled)).isEqualTo("CANCELLED");
        assertThat(taskStatus(processing)).isEqualTo("PROCESSING");
    }

    /** POSTs a valid DRAFT discount campaign over authenticated HTTP and returns its id. */
    private UUID createDraftCampaign() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Spring Sale",
                "type", "DISCOUNT",
                "startAt", START.toString(),
                "endAt", END.toString(),
                "ruleConfig",
                        Map.of(
                                "ruleType", "DISCOUNT",
                                "schema_version", 2,
                                "kind", "AMOUNT",
                                "amount", 100,
                                "thresholdMode", "NONE"),
                "targetSpec",
                        Map.of(
                                "kind",
                                "STATIC_LIST",
                                "listId",
                                UUID.randomUUID().toString()),
                "reachPlan", Map.of("channel", "EMAIL", "templateRef", "tpl-1", "timing", "SCHEDULED")));

        String response = mockMvc.perform(post("/internal/campaigns")
                        .with(httpBasic(OPERATOR_USER, OPERATOR_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    /** POSTs a lifecycle transition over authenticated HTTP and returns the campaign's new version. */
    private int transition(UUID campaignId, String targetStatus, int version) throws Exception {
        String response = mockMvc.perform(post("/internal/campaigns/" + campaignId + "/status")
                        .with(httpBasic(OPERATOR_USER, OPERATOR_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("targetStatus", targetStatus, "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(targetStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("version").asInt();
    }

    /** Seeds one reach_request (carrying the frozen reach_plan_snapshot) for the campaign. */
    private UUID seedRequest(UUID campaignId) {
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO reach_request
                    (id, campaign_id, trigger_type, send_cycle_key, reach_plan_snapshot, status, created_at)
                VALUES (?, ?, 'SCHEDULED_BATCH'::trigger_type, ?, ?::jsonb, 'DISPATCHING'::reach_request_status, ?)
                """,
                requestId,
                campaignId,
                "cycle-" + requestId,
                REACH_PLAN,
                Timestamp.from(Instant.now()));
        return requestId;
    }

    /** Seeds one EMAIL reach_task with the given lifecycle status for the request/campaign. */
    private UUID seedTask(UUID requestId, UUID campaignId, String status) {
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
                Timestamp.from(Instant.now()));
        return taskId;
    }

    private String taskStatus(UUID taskId) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM reach_task WHERE id = ?", String.class, taskId);
    }
}
