package com.example.campaignreach.campaign.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.campaign.domain.CampaignType;
import com.example.campaignreach.campaign.domain.rule.DiscountKind;
import com.example.campaignreach.campaign.domain.rule.DiscountRuleConfig;
import com.example.campaignreach.campaign.domain.rule.RuleConfigMapper;
import com.example.campaignreach.campaign.domain.rule.RuleConfigUpcaster;
import com.example.campaignreach.campaign.domain.rule.RuleConfigValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc slice for the internal campaign CRUD REST (task 4.1). The repository is mocked so the real
 * {@link RuleConfigMapper} validation gate and the controller/advice/security wiring are exercised
 * without a database. Test names map to the spec scenarios where applicable.
 */
@WebMvcTest(
        controllers = CampaignController.class,
        properties = {
            "campaignreach.security.operators[0].username=op",
            "campaignreach.security.operators[0].password=pw",
            "campaignreach.security.operators[0].id=00000000-0000-0000-0000-0000000000aa"
        })
@Import({
    CampaignController.class,
    CampaignApplicationService.class,
    CampaignApiExceptionHandler.class,
    CampaignSecurityConfig.class,
    RuleConfigMapper.class,
    RuleConfigValidator.class,
    RuleConfigUpcaster.class,
    CampaignControllerTest.TestBeans.class
})
class CampaignControllerTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(86_400);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampaignRepository repository;

    @Autowired
    private RuleConfigMapper ruleConfigMapper;

    @TestConfiguration
    static class TestBeans {
        @Bean
        CampaignRepository campaignRepository() {
            return Mockito.mock(CampaignRepository.class);
        }

        // The application service is @Transactional; the web slice has no JPA, so supply a
        // no-op transaction manager so the proxy can open/commit a (here trivial) transaction.
        @Bean
        org.springframework.transaction.PlatformTransactionManager transactionManager() {
            return new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(
                        Object transaction, org.springframework.transaction.TransactionDefinition definition) {}

                @Override
                protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {}

                @Override
                protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {}
            };
        }
    }

    // --- 建立折扣活動並落為草稿 ---

    @Test
    void 建立折扣活動並落為草稿() throws Exception {
        when(repository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Spring Sale",
                "type", "DISCOUNT",
                "startAt", START.toString(),
                "endAt", END.toString(),
                "ruleConfig",
                        Map.of(
                                "ruleType",
                                "DISCOUNT",
                                "schema_version",
                                2,
                                "kind",
                                "AMOUNT",
                                "amount",
                                100,
                                "thresholdMode",
                                "NONE"),
                "targetSpec",
                        Map.of(
                                "kind",
                                "STATIC_LIST",
                                "listId",
                                UUID.randomUUID().toString()),
                "reachPlan", Map.of("channel", "EMAIL", "templateRef", "tpl-1", "timing", "SCHEDULED")));

        mockMvc.perform(post("/internal/campaigns")
                        .with(httpBasic("op", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void invalidRuleConfigRejectedWithReasons() throws Exception {
        // percentage > 100 is rejected by the RuleConfigValidator (FR-005).
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Bad Sale",
                "type", "DISCOUNT",
                "startAt", START.toString(),
                "endAt", END.toString(),
                "ruleConfig",
                        Map.of(
                                "ruleType", "DISCOUNT",
                                "schema_version", 2,
                                "kind", "PERCENTAGE",
                                "percentage", 150,
                                "thresholdMode", "NONE"),
                "targetSpec", Map.of("kind", "CONDITION", "conditions", Map.of("tier", "gold")),
                "reachPlan", Map.of("channel", "EMAIL", "templateRef", "tpl-1", "timing", "SCHEDULED")));

        mockMvc.perform(post("/internal/campaigns")
                        .with(httpBasic("op", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("must not exceed 100%")));
    }

    @Test
    void independentUpdateOfReachSettingsKeepsRuleConfig() throws Exception {
        UUID id = UUID.randomUUID();
        Campaign stored = discountCampaign(id);
        when(repository.findById(id)).thenReturn(Optional.of(stored));
        when(repository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        // Update only reachPlan; ruleConfig is omitted (null) → must be left unchanged.
        String body = objectMapper.writeValueAsString(Map.of(
                "version", stored.getVersion(),
                "reachPlan", Map.of("channel", "SMS", "templateRef", "tpl-2", "timing", "EVENT")));

        mockMvc.perform(put("/internal/campaigns/" + id)
                        .with(httpBasic("op", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachPlan.channel").value("SMS"))
                .andExpect(jsonPath("$.ruleConfig.kind").value("AMOUNT"))
                .andExpect(jsonPath("$.ruleConfig.amount").value(100));
    }

    @Test
    void independentUpdateOfRuleSettingsKeepsReachPlan() throws Exception {
        UUID id = UUID.randomUUID();
        Campaign stored = discountCampaign(id);
        when(repository.findById(id)).thenReturn(Optional.of(stored));
        when(repository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        // Update only ruleConfig; reachPlan omitted → must be left unchanged (EMAIL/tpl-1/SCHEDULED).
        String body = objectMapper.writeValueAsString(Map.of(
                "version",
                stored.getVersion(),
                "ruleConfig",
                Map.of(
                        "ruleType",
                        "DISCOUNT",
                        "schema_version",
                        2,
                        "kind",
                        "AMOUNT",
                        "amount",
                        250,
                        "thresholdMode",
                        "NONE")));

        mockMvc.perform(put("/internal/campaigns/" + id)
                        .with(httpBasic("op", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleConfig.amount").value(250))
                .andExpect(jsonPath("$.reachPlan.channel").value("EMAIL"))
                .andExpect(jsonPath("$.reachPlan.templateRef").value("tpl-1"));
    }

    @Test
    void staleVersionUpdateReturnsConflict() throws Exception {
        UUID id = UUID.randomUUID();
        Campaign stored = discountCampaign(id); // stored version is 0
        when(repository.findById(id)).thenReturn(Optional.of(stored));

        // Client echoes a version that a concurrent edit has already advanced past → 409, no save.
        String body = objectMapper.writeValueAsString(Map.of("version", stored.getVersion() + 1, "name", "Renamed"));

        mockMvc.perform(put("/internal/campaigns/" + id)
                        .with(httpBasic("op", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    void getMissingCampaignReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/campaigns/" + id).with(httpBasic("op", "pw")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    // --- auth (§10): 未經身分驗證/授權呼叫內部 REST THEN 拒絕存取 ---

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/internal/campaigns/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedNonOperatorRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/internal/campaigns/" + UUID.randomUUID())
                        .with(user("intruder").roles("VIEWER")))
                .andExpect(status().isForbidden());
    }

    // --- 4.2 活動狀態切換 (FR-011, US-002) ---

    @Test
    void 合法狀態切換() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        // DRAFT → SCHEDULED
        when(repository.findById(id)).thenReturn(Optional.of(discountCampaign(id, CampaignStatus.DRAFT)));
        mockMvc.perform(post("/internal/campaigns/" + id + "/status")
                        .with(httpBasic("op", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "SCHEDULED", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        // RUNNING → PAUSED (a later edge in the chain)
        when(repository.findById(id)).thenReturn(Optional.of(discountCampaign(id, CampaignStatus.RUNNING)));
        mockMvc.perform(post("/internal/campaigns/" + id + "/status")
                        .with(httpBasic("op", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "PAUSED", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void 不合理狀態切換被擋下() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(discountCampaign(id, CampaignStatus.ENDED)));

        // ENDED is terminal: ENDED → RUNNING is illegal → 422.
        mockMvc.perform(post("/internal/campaigns/" + id + "/status")
                        .with(httpBasic("op", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "RUNNING", "version", 0))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("illegal_transition"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ENDED -> RUNNING")));
    }

    @Test
    void staleVersionTransitionReturnsConflict() throws Exception {
        UUID id = UUID.randomUUID();
        Campaign stored = discountCampaign(id, CampaignStatus.DRAFT); // stored version is 0
        when(repository.findById(id)).thenReturn(Optional.of(stored));

        mockMvc.perform(post("/internal/campaigns/" + id + "/status")
                        .with(httpBasic("op", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "SCHEDULED", "version", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    void missingTargetStatusIsRejected() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/internal/campaigns/" + id + "/status")
                        .with(httpBasic("op", "pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("version", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    private Campaign discountCampaign(UUID id) {
        return discountCampaign(id, CampaignStatus.DRAFT);
    }

    private Campaign discountCampaign(UUID id, CampaignStatus status) {
        DiscountRuleConfig rule = new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION,
                DiscountKind.AMOUNT,
                new BigDecimal("100"),
                null,
                com.example.campaignreach.campaign.domain.rule.ThresholdMode.NONE,
                null);
        String ruleJson = ruleConfigMapper.toJson(rule, CampaignType.DISCOUNT, START, END);
        String targetJson = "{\"kind\":\"STATIC_LIST\",\"listId\":\"" + UUID.randomUUID() + "\"}";
        String reachJson = "{\"channel\":\"EMAIL\",\"templateRef\":\"tpl-1\",\"timing\":\"SCHEDULED\"}";
        return new Campaign(
                id, "Spring Sale", CampaignType.DISCOUNT, status, START, END, ruleJson, targetJson, reachJson);
    }
}
