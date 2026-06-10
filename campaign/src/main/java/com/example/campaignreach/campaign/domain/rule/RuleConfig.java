package com.example.campaignreach.campaign.domain.rule;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Strongly-typed offer rule configuration ({@code RuleConfig} DTO; class-model "規則設定" package).
 *
 * <p>One concrete subtype per {@link CampaignType}: {@link DiscountRuleConfig} (DISCOUNT),
 * {@link GiftAddonRuleConfig} (GIFT_ADDON) and {@link FlashSaleRuleConfig} (FLASH_SALE). Each
 * carries a {@code schemaVersion} (serialized as {@code schema_version} in the {@code rule_config}
 * JSONB) so older payloads can be read back through the application-layer upcaster without any DB
 * migration (spec §4, FR-002/FR-005).
 *
 * <p>Jackson is configured for polymorphic (de)serialization: the persisted JSON keeps a {@code
 * ruleType} discriminator that maps 1:1 to {@link CampaignType}. {@link RuleConfigMapper} is the
 * single entry point that validates before serializing and upcasts after parsing.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "ruleType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DiscountRuleConfig.class, name = "DISCOUNT"),
    @JsonSubTypes.Type(value = GiftAddonRuleConfig.class, name = "GIFT_ADDON"),
    @JsonSubTypes.Type(value = FlashSaleRuleConfig.class, name = "FLASH_SALE")
})
public sealed interface RuleConfig permits DiscountRuleConfig, GiftAddonRuleConfig, FlashSaleRuleConfig {

    /** Schema version of this payload; persisted as {@code schema_version} in the JSONB. */
    int schemaVersion();

    /** The {@link CampaignType} this config applies to — must match the owning campaign's type. */
    CampaignType campaignType();
}
