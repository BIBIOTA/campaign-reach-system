package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.campaign.domain.CurrentOperator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP integration test for the internal campaign API (tasks 4.1 / 4.2), booting the full
 * Spring context on a real PostgreSQL container. Unlike {@code CampaignControllerTest} (a MockMvc
 * slice with a mocked repository) and {@code CampaignPersistenceIntegrationTest} (which drives the
 * JPA layer directly), this test exercises the complete request path —
 * <em>HTTP Basic auth → {@link com.example.campaignreach.campaign.api.OperatorAuditFilter} →
 * {@link CurrentOperator} → Spring Data JPA auditing → real DB</em> — so the §10 audit-attribution
 * chain and the 4.2 lifecycle transition are verified against real persistence, not mocks.
 *
 * <p>Auto-skipped without Docker via the inherited {@link RequiresDocker} condition.
 */
@AutoConfigureMockMvc
class CampaignApiIntegrationTest extends AbstractIntegrationTest {

    /** Matches the throwaway operator wired in {@link AbstractIntegrationTest#infrastructureProperties}. */
    private static final String OPERATOR_USER = "it-operator";

    private static final String OPERATOR_PASS = "it-operator-secret";
    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(86_400);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampaignRepository repository;

    @AfterEach
    void clearOperator() {
        CurrentOperator.clear();
    }

    /**
     * Scenario (§10, FR-001): creating a campaign over authenticated HTTP attributes the authenticated
     * operator id to {@code created_by}/{@code updated_by}. This is the only test that exercises the
     * full chain (principal → OperatorAuditFilter → CurrentOperator → audit columns) against a real DB.
     */
    @Test
    void creatingCampaignViaHttpAttributesAuthenticatedOperatorToAuditColumns() throws Exception {
        UUID id = createDraftCampaign().id();

        Campaign persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.getCreatedBy()).isEqualTo(OPERATOR_ID);
        assertThat(persisted.getUpdatedBy()).isEqualTo(OPERATOR_ID);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    /**
     * Scenario (4.2, FR-011): a legal lifecycle transition over HTTP persists the new status and the
     * optimistic-lock version advances — behaviour the mocked-save slice test cannot observe because
     * its stub never bumps the version.
     */
    @Test
    void legalTransitionViaHttpPersistsStatusAndBumpsVersion() throws Exception {
        Created created = createDraftCampaign();

        mockMvc.perform(post("/internal/campaigns/" + created.id() + "/status")
                        .with(httpBasic(OPERATOR_USER, OPERATOR_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("targetStatus", "SCHEDULED", "version", created.version()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        Campaign reloaded = repository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(reloaded.getVersion()).isGreaterThan(created.version());
    }

    /**
     * Scenario (4.2, FR-011): an illegal lifecycle edge is rejected with 422 and leaves the stored
     * status and version untouched (no partial write).
     */
    @Test
    void illegalTransitionViaHttpReturns422AndLeavesStateUnchanged() throws Exception {
        Created created = createDraftCampaign();

        // DRAFT → RUNNING is not a legal edge (must go through SCHEDULED first).
        mockMvc.perform(post("/internal/campaigns/" + created.id() + "/status")
                        .with(httpBasic(OPERATOR_USER, OPERATOR_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("targetStatus", "RUNNING", "version", created.version()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("illegal_transition"));

        Campaign reloaded = repository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(reloaded.getVersion()).isEqualTo(created.version());
    }

    /**
     * Scenario (4.1, FR-001): a successful PATCH must return the <em>new</em> optimistic-lock version
     * in its response body, so a client chaining a follow-up write (a further PATCH or a POST /status)
     * echoes a current value rather than the stale one it sent — otherwise the next write fails with a
     * spurious 409. Regression guard: with a plain {@code save()} (no flush) the deferred {@code
     * @Version} increment is not yet materialised when the response view is built, so the body would
     * carry the stale pre-update version. Mirrors {@link #legalTransitionViaHttpPersistsStatusAndBumpsVersion}.
     */
    @Test
    void updateViaHttpReturnsBumpedVersionInResponse() throws Exception {
        Created created = createDraftCampaign();

        String response = mockMvc.perform(patch("/internal/campaigns/" + created.id())
                        .with(httpBasic(OPERATOR_USER, OPERATOR_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("version", created.version(), "name", "Spring Sale (renamed)"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spring Sale (renamed)"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int returnedVersion = objectMapper.readTree(response).get("version").asInt();
        assertThat(returnedVersion).isGreaterThan(created.version());

        // The body's version matches what is actually persisted, so it is safe to chain a follow-up write.
        Campaign reloaded = repository.findById(created.id()).orElseThrow();
        assertThat(returnedVersion).isEqualTo(reloaded.getVersion());
    }

    /** POSTs a valid DRAFT discount campaign over authenticated HTTP and returns its id + version. */
    private Created createDraftCampaign() throws Exception {
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
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return new Created(
                UUID.fromString(json.get("id").asText()), json.get("version").asInt());
    }

    private record Created(UUID id, int version) {}
}
