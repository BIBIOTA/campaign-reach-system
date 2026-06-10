package com.example.campaignreach.campaign.domain.rule;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Application-layer upcaster: transforms an older {@code schema_version} {@code rule_config} JSON
 * tree into the current DTO structure on read, with <b>no database migration</b> (spec §4, FR-002).
 *
 * <p>It operates on the parsed {@link JsonNode} <em>before</em> binding to a {@link RuleConfig} DTO,
 * so newly required fields can be filled with their current-version defaults. The mechanism is a
 * simple per-version step applied in sequence until the node reaches the current version — extend by
 * adding the next {@code vN -> vN+1} branch when a schema evolves. YAGNI: only the discount
 * {@code v1 -> v2} step that real data needs is implemented.
 *
 * <p>Concrete v1 -&gt; v2 discount upcast: schema v1 predates the spend-threshold feature (FR-003),
 * so a v1 payload has no {@code thresholdMode}. The upcaster defaults it to {@link
 * ThresholdMode#NONE} (無門檻), matching v1's effective "always applies" behavior.
 */
@Component
public class RuleConfigUpcaster {

    /**
     * Returns a node whose {@code schema_version} equals the current version for the given type,
     * defaulting any fields introduced by intervening versions.
     *
     * @param type the campaign type the node represents
     * @param node the parsed (possibly old-version) {@code rule_config} JSON
     * @return the same node mutated up to the current schema version
     */
    public JsonNode upcast(CampaignType type, JsonNode node) {
        if (type == CampaignType.DISCOUNT && node instanceof ObjectNode obj) {
            return upcastDiscount(obj);
        }
        return node;
    }

    private JsonNode upcastDiscount(ObjectNode obj) {
        int version = obj.path("schema_version").asInt(1);
        if (version < 2) {
            // v1 -> v2: spend threshold did not exist; default to NONE (無門檻).
            if (!obj.hasNonNull("thresholdMode")) {
                obj.put("thresholdMode", ThresholdMode.NONE.name());
            }
            obj.put("schema_version", DiscountRuleConfig.CURRENT_SCHEMA_VERSION);
        }
        return obj;
    }
}
