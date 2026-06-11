package com.example.campaignreach.reach.metrics;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.campaignreach.reach.orchestrator.ReachTaskStatus;
import com.example.campaignreach.shared.event.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc slice for the internal reach-metrics REST (task 10.3). The query service is mocked so the
 * controller/serialization/security wiring is exercised without a database. Test names map to the
 * spec scenarios. The {@code /internal/**} OPERATOR HTTP-Basic chain here mirrors the production
 * {@code CampaignSecurityConfig} (which the reach module cannot import across the module boundary),
 * so the same auth contract is asserted for the reach endpoints.
 */
@WebMvcTest(controllers = ReachMetricsController.class)
@Import({ReachMetricsController.class, ReachMetricsControllerTest.TestSecurity.class})
class ReachMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReachMetricsQueryService service;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @TestConfiguration
    static class TestSecurity {
        @Bean
        ReachMetricsQueryService reachMetricsQueryService() {
            return Mockito.mock(ReachMetricsQueryService.class);
        }

        @Bean
        InMemoryUserDetailsManager users() {
            return new InMemoryUserDetailsManager(User.withUsername("op")
                    .password("{noop}pw")
                    .roles("OPERATOR")
                    .build());
        }

        @Bean
        SecurityFilterChain testInternalChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/internal/**")
                    .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("OPERATOR"))
                    .httpBasic(basic -> {})
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .build();
        }
    }

    @Test
    void 活動維度彙總() throws Exception {
        UUID campaignId = UUID.randomUUID();
        Map<ReachTaskStatus, Long> counts = new EnumMap<>(ReachTaskStatus.class);
        counts.put(ReachTaskStatus.PENDING, 2L);
        counts.put(ReachTaskStatus.SENT, 6L);
        counts.put(ReachTaskStatus.FAILED, 2L);
        when(service.campaignMetrics(campaignId)).thenReturn(CampaignReachMetrics.fromStatusCounts(campaignId, counts));

        mockMvc.perform(get("/internal/reach/campaigns/" + campaignId + "/metrics")
                        .with(httpBasic("op", "pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.totalCount").value(10))
                .andExpect(jsonPath("$.deliveredRate").value(0.6))
                .andExpect(jsonPath("$.failedRate").value(0.2))
                .andExpect(jsonPath("$.statusDistribution.SENT").value(6))
                .andExpect(jsonPath("$.statusDistribution.PENDING").value(2))
                .andExpect(jsonPath("$.statusDistribution.DLQ").value(0));
    }

    @Test
    void 單筆收件人狀態() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(service.recipientStatus(campaignId, userId))
                .thenReturn(new RecipientReachView(
                        campaignId,
                        userId,
                        List.of(
                                new RecipientReachStatus(
                                        campaignId, userId, Channel.EMAIL, "cycle-1", ReachTaskStatus.SENT),
                                new RecipientReachStatus(
                                        campaignId, userId, Channel.SMS, "cycle-1", ReachTaskStatus.PENDING))));

        mockMvc.perform(get("/internal/reach/campaigns/" + campaignId + "/recipients/" + userId)
                        .with(httpBasic("op", "pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.statuses.length()").value(2))
                .andExpect(jsonPath("$.statuses[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$.statuses[0].status").value("SENT"))
                .andExpect(jsonPath("$.statuses[1].status").value("PENDING"))
                // PII minimization (NFR-005): only identifiers + status — never email/content.
                .andExpect(jsonPath("$.statuses[0].email").doesNotExist())
                .andExpect(jsonPath("$.statuses[0].message").doesNotExist())
                .andExpect(jsonPath("$.statuses[0].sendCycleKey").value("cycle-1"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/internal/reach/campaigns/" + UUID.randomUUID() + "/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedNonOperatorRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/internal/reach/campaigns/" + UUID.randomUUID() + "/metrics")
                        .with(user("intruder").roles("VIEWER")))
                .andExpect(status().isForbidden());
    }
}
