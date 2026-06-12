package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.campaign.domain.rule.RuleConfig;
import com.example.campaignreach.campaign.domain.rule.RuleConfigMapper;
import com.example.campaignreach.campaign.domain.rule.RuleConfigValidationException;
import com.example.campaignreach.shared.event.CampaignStatusChanged;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the internal campaign CRUD endpoints (task 4.1).
 *
 * <p>Owns the trust-boundary work the controller delegates: it validates and serializes the offer
 * rule through {@link RuleConfigMapper} (FR-005), serializes the typed target/reach DTOs to the raw
 * JSON columns, and creates campaigns in {@code DRAFT} (FR-006). Offer-rule settings and
 * reach/target settings are updated independently — a {@code null} field on the update request
 * leaves the stored value unchanged (FR-001/FR-007/FR-010). Optimistic-lock conflicts propagate
 * from the repository {@code save} unchanged so the controller can map them to HTTP 409.
 */
@Service
public class CampaignApplicationService {

    private final CampaignRepository repository;
    private final RuleConfigMapper ruleConfigMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Dedicated codec owning the {@code target_spec} / {@code reach_plan} JSON column format. Like
     * {@link RuleConfigMapper}, this service owns its own {@link ObjectMapper} so the persisted column
     * format cannot drift when the web-layer mapper is reconfigured.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Wires the campaign repository and the rule validation/serialization mapper. */
    public CampaignApplicationService(
            CampaignRepository repository,
            RuleConfigMapper ruleConfigMapper,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.ruleConfigMapper = ruleConfigMapper;
        this.eventPublisher = eventPublisher;
    }

    /** Creates a campaign in {@code DRAFT} (FR-006), validating the rule before persist. Returns the new id + version. */
    @Transactional
    public CreateCampaignResponse create(CreateCampaignRequest request) {
        String ruleJson =
                ruleConfigMapper.toJson(request.ruleConfig(), request.type(), request.startAt(), request.endAt());
        Campaign campaign = new Campaign(
                UUID.randomUUID(),
                request.name(),
                request.type(),
                CampaignStatus.DRAFT,
                request.startAt(),
                request.endAt(),
                ruleJson,
                toJson(request.targetSpec()),
                toJson(request.reachPlan()));
        Campaign saved = repository.save(campaign);
        return new CreateCampaignResponse(saved.getId(), saved.getVersion(), saved.getStatus());
    }

    /** Reads one campaign, parsing the rule/target/reach JSON back to typed views. */
    @Transactional(readOnly = true)
    public CampaignView get(UUID id) {
        return toView(require(id));
    }

    /**
     * Modifies a campaign. Each {@code null} field is left unchanged so offer-rule settings and
     * reach/target settings can be saved independently. Re-validates the rule when changed. Status is
     * never altered here (task 4.2 owns lifecycle).
     *
     * <p>Enforces read-then-update optimistic locking (FR-001): the client must echo the {@code
     * version} it read; if a concurrent edit has since advanced the stored version, the later writer
     * fails with {@link ObjectOptimisticLockingFailureException} (mapped to HTTP 409) rather than
     * silently overwriting the other operator's change.
     */
    @Transactional
    public CampaignView update(UUID id, UpdateCampaignRequest request) {
        Campaign campaign = require(id);
        assertVersionMatches(campaign, request.version());

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new RuleConfigValidationException(List.of("name must not be blank"));
            }
            campaign.setName(request.name());
        }
        if (request.startAt() != null) {
            campaign.setStartAt(request.startAt());
        }
        if (request.endAt() != null) {
            campaign.setEndAt(request.endAt());
        }
        if (request.ruleConfig() != null) {
            String ruleJson = ruleConfigMapper.toJson(
                    request.ruleConfig(), campaign.getType(), campaign.getStartAt(), campaign.getEndAt());
            campaign.setRuleConfig(ruleJson);
        } else if (request.startAt() != null || request.endAt() != null) {
            RuleConfig existingRule = ruleConfigMapper.fromJson(campaign.getType(), campaign.getRuleConfig());
            String ruleJson = ruleConfigMapper.toJson(
                    existingRule, campaign.getType(), campaign.getStartAt(), campaign.getEndAt());
            campaign.setRuleConfig(ruleJson);
        }
        if (request.targetSpec() != null) {
            campaign.setTargetSpec(toJson(request.targetSpec()));
        }
        if (request.reachPlan() != null) {
            campaign.setReachPlan(toJson(request.reachPlan()));
        }

        // saveAndFlush so the @Version increment Hibernate defers until flush is materialised before
        // toView reads it: the response must carry the *new* version so a client chaining a follow-up
        // write (a further PATCH or a POST /status) echoes a current value rather than the stale one it
        // sent, which would otherwise fail the next write with a spurious 409. Mirrors transition().
        Campaign saved = repository.saveAndFlush(campaign);
        return toView(saved);
    }

    /**
     * Performs an operator-driven lifecycle transition (FR-011). Loads the campaign (404 if absent),
     * enforces read-then-update optimistic locking exactly like {@link #update} (a stale {@code
     * version} fails with {@link ObjectOptimisticLockingFailureException} → HTTP 409), then delegates
     * to {@link Campaign#transitionTo} which rejects illegal edges (→ HTTP 422). Status is the only
     * field changed.
     */
    @Transactional
    public CampaignView transition(UUID id, ChangeCampaignStatusRequest request) {
        Campaign campaign = require(id);
        assertVersionMatches(campaign, request.version());
        CampaignStatus previousStatus = campaign.getStatus();
        campaign.transitionTo(request.targetStatus());
        // saveAndFlush so the @Version increment Hibernate defers until flush is materialised before
        // toView reads it: the response must carry the *new* version so an operator chaining lifecycle
        // transitions (e.g. SCHEDULED → RUNNING → PAUSED) echoes a current value rather than a stale one
        // that would fail the next write with a spurious 409. Mirrors CampaignLifecycleScheduler.
        Campaign saved = repository.saveAndFlush(campaign);
        publishStatusChanged(saved, previousStatus);
        return toView(saved);
    }

    private void publishStatusChanged(Campaign campaign, CampaignStatus previousStatus) {
        eventPublisher.publishEvent(new CampaignStatusChanged(
                campaign.getId(), previousStatus.name(), campaign.getStatus().name(), Instant.now()));
    }

    private Campaign require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new CampaignNotFoundException(id));
    }

    /**
     * Enforces read-then-update optimistic locking (FR-001): the client-echoed {@code version} must
     * match the loaded campaign's current version. A {@code null} or stale value fails with {@link
     * ObjectOptimisticLockingFailureException} (mapped to HTTP 409) so a concurrent writer cannot
     * silently overwrite another operator's change.
     */
    private void assertVersionMatches(Campaign campaign, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != campaign.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Campaign.class, campaign.getId());
        }
    }

    private CampaignView toView(Campaign campaign) {
        RuleConfig rule = ruleConfigMapper.fromJson(campaign.getType(), campaign.getRuleConfig());
        return new CampaignView(
                campaign.getId(),
                campaign.getName(),
                campaign.getType(),
                campaign.getStatus(),
                campaign.getStartAt(),
                campaign.getEndAt(),
                rule,
                fromJson(campaign.getTargetSpec(), TargetSpecDto.class),
                fromJson(campaign.getReachPlan(), ReachPlanDto.class),
                campaign.getVersion(),
                campaign.getCreatedBy(),
                campaign.getUpdatedBy(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuleConfigValidationException(List.of("failed to serialize settings: " + e.getOriginalMessage()));
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse stored settings from database", e);
        }
    }
}
