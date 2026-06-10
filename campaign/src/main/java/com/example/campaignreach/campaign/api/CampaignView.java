package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.campaign.domain.CampaignType;
import com.example.campaignreach.campaign.domain.rule.RuleConfig;
import java.time.Instant;
import java.util.UUID;

/**
 * Read view of a campaign returned by the query/modify endpoints (task 4.1).
 *
 * <p>{@code ruleConfig} is the parsed strongly-typed {@link RuleConfig} (read back via {@code
 * RuleConfigMapper.fromJson}); {@code targetSpec} / {@code reachPlan} are the typed wire DTOs. The
 * optimistic-lock {@code version} and audit fields are surfaced so a client can read-then-update.
 *
 * @param id campaign identity
 * @param name campaign name
 * @param type offer type
 * @param status lifecycle status (always {@code DRAFT} until task 4.2 lifecycle transitions)
 * @param startAt active-period start
 * @param endAt active-period end
 * @param ruleConfig parsed strongly-typed offer rule
 * @param targetSpec audience selection settings
 * @param reachPlan channel/template/timing settings
 * @param version optimistic-lock version for read-then-update
 * @param createdBy operator who created the campaign
 * @param updatedBy operator who last modified the campaign
 * @param createdAt creation timestamp
 * @param updatedAt last-modification timestamp
 */
public record CampaignView(
        UUID id,
        String name,
        CampaignType type,
        CampaignStatus status,
        Instant startAt,
        Instant endAt,
        RuleConfig ruleConfig,
        TargetSpecDto targetSpec,
        ReachPlanDto reachPlan,
        int version,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt) {}
