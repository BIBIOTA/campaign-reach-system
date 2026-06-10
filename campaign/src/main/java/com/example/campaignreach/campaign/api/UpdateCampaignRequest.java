package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.rule.RuleConfig;
import jakarta.validation.Valid;
import java.time.Instant;

/**
 * Modify-campaign request for {@code PUT /internal/campaigns/{id}} (task 4.1, FR-001/FR-007/FR-010).
 *
 * <p>Offer-rule settings and reach/target settings are updated <strong>independently</strong>: any
 * {@code null} field is left unchanged, so a caller can patch only {@link #ruleConfig}, only {@link
 * #reachPlan}, etc. {@code status} is intentionally NOT updatable here — lifecycle transitions are
 * task 4.2. {@code version} is the optimistic-lock guard supplied by the client; a stale value
 * surfaces as HTTP 409.
 *
 * @param version the campaign version the client read; concurrent edits with a stale value fail
 * @param name new name; {@code null} leaves it unchanged
 * @param startAt new period start; {@code null} leaves it unchanged
 * @param endAt new period end; {@code null} leaves it unchanged
 * @param ruleConfig new per-type offer rule; {@code null} leaves the stored rule unchanged
 * @param targetSpec new targeting settings; {@code null} leaves them unchanged
 * @param reachPlan new reach settings; {@code null} leaves them unchanged
 */
public record UpdateCampaignRequest(
        Integer version,
        String name,
        Instant startAt,
        Instant endAt,
        RuleConfig ruleConfig,
        @Valid TargetSpecDto targetSpec,
        @Valid ReachPlanDto reachPlan) {}
