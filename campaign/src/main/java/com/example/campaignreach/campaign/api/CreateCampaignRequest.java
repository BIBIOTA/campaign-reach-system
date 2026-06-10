package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.example.campaignreach.campaign.domain.rule.RuleConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Create-campaign request for {@code POST /internal/campaigns} (task 4.1, FR-001/FR-006).
 *
 * <p>Carries the offer-rule settings ({@link #ruleConfig}, validated per type via {@code
 * RuleConfigMapper} before persist) and the reach/target settings ({@link #targetSpec} / {@link
 * #reachPlan}). The created campaign always starts in {@code DRAFT}; the {@code status} is never
 * accepted from the client (lifecycle transitions are task 4.2).
 *
 * @param name campaign name (required, non-blank)
 * @param type campaign offer type; drives which {@link RuleConfig} subtype is expected
 * @param startAt active-period start
 * @param endAt active-period end
 * @param ruleConfig the per-type offer rule (polymorphic on {@code ruleType})
 * @param targetSpec audience targeting settings
 * @param reachPlan reach delivery settings
 */
public record CreateCampaignRequest(
        @NotBlank(message = "name must not be blank") String name,
        @NotNull(message = "type must not be null") CampaignType type,
        @NotNull(message = "startAt must not be null") Instant startAt,
        @NotNull(message = "endAt must not be null") Instant endAt,
        @NotNull(message = "ruleConfig must not be null") RuleConfig ruleConfig,
        @NotNull(message = "targetSpec must not be null") @Valid TargetSpecDto targetSpec,
        @NotNull(message = "reachPlan must not be null") @Valid ReachPlanDto reachPlan) {}
