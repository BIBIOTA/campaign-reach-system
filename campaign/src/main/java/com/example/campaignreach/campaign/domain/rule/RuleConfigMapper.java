package com.example.campaignreach.campaign.domain.rule;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Single entry point between the strongly-typed {@link RuleConfig} DTOs and the raw {@code
 * rule_config} JSONB column ({@link String}) owned by the {@link
 * com.example.campaignreach.campaign.domain.Campaign} aggregate (spec §4).
 *
 * <ul>
 *   <li>{@link #toJson} validates (FR-005) then serializes — the JSON always carries {@code
 *       schema_version} and a {@code ruleType} discriminator.
 *   <li>{@link #fromJson} parses, runs the {@link RuleConfigUpcaster} for backward-compatible reads
 *       (FR-002), then binds to the current DTO — dispatching on {@link CampaignType}.
 * </ul>
 */
@Component
public class RuleConfigMapper {

    private final ObjectMapper objectMapper;
    private final RuleConfigValidator validator;
    private final RuleConfigUpcaster upcaster;

    /** Wires the validator and upcaster and configures a JSR-310-aware {@link ObjectMapper}. */
    public RuleConfigMapper(RuleConfigValidator validator, RuleConfigUpcaster upcaster) {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.validator = validator;
        this.upcaster = upcaster;
    }

    /**
     * Validates the rule against the owning campaign's type/period and serializes it to a JSONB
     * string. The serialized JSON always includes {@code schema_version}.
     *
     * @throws RuleConfigValidationException if validation fails (FR-005)
     */
    public String toJson(RuleConfig config, CampaignType campaignType, Instant startAt, Instant endAt) {
        validator.validate(config, campaignType, startAt, endAt);
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new RuleConfigValidationException(
                    List.of("failed to serialize ruleConfig: " + e.getOriginalMessage()));
        }
    }

    /**
     * Parses a {@code rule_config} JSONB string into the current DTO, upcasting older {@code
     * schema_version} payloads first (FR-002) — no DB migration required.
     *
     * @param campaignType the owning campaign's type, used to dispatch and to upcast
     * @param json the persisted JSONB string
     */
    public RuleConfig fromJson(CampaignType campaignType, String json) {
        try {
            JsonNode parsed = objectMapper.readTree(json);
            JsonNode upcasted = upcaster.upcast(campaignType, parsed);
            // Ensure the polymorphic discriminator matches the owning campaign type so reads do not
            // depend on a (historically optional) ruleType field being present in old payloads.
            if (upcasted instanceof ObjectNode obj) {
                obj.put("ruleType", campaignType.name());
            }
            return objectMapper.treeToValue(upcasted, targetType(campaignType));
        } catch (JsonProcessingException e) {
            throw new RuleConfigValidationException(List.of("failed to parse ruleConfig: " + e.getOriginalMessage()));
        }
    }

    private Class<? extends RuleConfig> targetType(CampaignType campaignType) {
        return switch (campaignType) {
            case DISCOUNT -> DiscountRuleConfig.class;
            case GIFT_ADDON -> GiftAddonRuleConfig.class;
            case FLASH_SALE -> FlashSaleRuleConfig.class;
        };
    }
}
