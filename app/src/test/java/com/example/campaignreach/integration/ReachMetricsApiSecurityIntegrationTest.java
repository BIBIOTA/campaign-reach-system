package com.example.campaignreach.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Production auth-wiring guard for the reach-metrics endpoints (task 10.3, §10, NFR-005), booting the
 * full Spring context so the <em>real</em> {@code CampaignSecurityConfig} chain is in play — not the
 * mirrored {@code TestSecurity} chain of the {@code ReachMetricsControllerTest} MockMvc slice.
 *
 * <p>The reach module declares no {@link org.springframework.security.web.SecurityFilterChain} of its
 * own: {@code /internal/reach/**} is protected solely because the campaign module's {@code
 * securityMatcher("/internal/**")} happens to cover it. That coupling is invisible to the slice test
 * (which asserts auth against a copy of the chain), so this test pins it against the production chain:
 * if the campaign matcher were ever narrowed (e.g. to {@code /internal/campaigns/**}), the reach
 * endpoints would silently become public and this test — not a unit test — would catch it.
 *
 * <p>Auto-skipped without Docker via the inherited {@link RequiresDocker} condition.
 */
@AutoConfigureMockMvc
class ReachMetricsApiSecurityIntegrationTest extends AbstractIntegrationTest {

    /** Matches the throwaway operator wired in {@link AbstractIntegrationTest#infrastructureProperties}. */
    private static final String OPERATOR_USER = "it-operator";

    private static final String OPERATOR_PASS = "it-operator-secret";

    @Autowired
    private MockMvc mockMvc;

    /** An anonymous call to the reach-metrics endpoint is rejected with 401 by the production chain. */
    @Test
    void anonymousRequestToReachMetricsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/reach/campaigns/" + UUID.randomUUID() + "/metrics"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * An authenticated operator reaches the controller (proving the production {@code /internal/**}
     * matcher covers {@code /internal/reach/**}) and gets a well-formed 200 for a campaign with no
     * recipients: zero total, both rates {@code 0.0} — no division by zero, no 404/500.
     */
    @Test
    void authenticatedOperatorReachesControllerAndGetsEmptyMetrics() throws Exception {
        mockMvc.perform(get("/internal/reach/campaigns/" + UUID.randomUUID() + "/metrics")
                        .with(httpBasic(OPERATOR_USER, OPERATOR_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.deliveredRate").value(0.0))
                .andExpect(jsonPath("$.failedRate").value(0.0));
    }
}
