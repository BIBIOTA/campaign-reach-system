package com.example.campaignreach.campaign.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.campaignreach.campaign.domain.CampaignType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Fast (no-DB) unit tests for RuleConfig schema validation, JSONB (de)serialization and the
 * application-layer upcaster (spec §4 / FR-002 / FR-003 / FR-005). Test names map to the spec
 * scenarios.
 */
class RuleConfigMapperTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(86_400);

    private final RuleConfigMapper mapper = new RuleConfigMapper(new RuleConfigValidator(), new RuleConfigUpcaster());

    // --- 合法規則通過驗證後落庫 (含 schema_version) ---

    @Test
    void validDiscountConfigPassesValidationAndSerializesWithSchemaVersion() {
        DiscountRuleConfig config = new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION,
                DiscountKind.AMOUNT,
                new BigDecimal("100"),
                null,
                ThresholdMode.NONE,
                null);

        String json = mapper.toJson(config, CampaignType.DISCOUNT, START, END);

        assertThat(json).contains("\"schema_version\":2").contains("\"ruleType\":\"DISCOUNT\"");
    }

    @Test
    void validGiftAddonConfigPassesValidationAndSerializesWithSchemaVersion() {
        GiftAddonRuleConfig config =
                new GiftAddonRuleConfig(GiftAddonRuleConfig.CURRENT_SCHEMA_VERSION, "SKU-GIFT-1", 1);

        String json = mapper.toJson(config, CampaignType.GIFT_ADDON, START, END);

        assertThat(json).contains("\"schema_version\":1").contains("\"ruleType\":\"GIFT_ADDON\"");
    }

    @Test
    void validFlashSaleConfigPassesValidationAndSerializesWithSchemaVersion() {
        FlashSaleRuleConfig config =
                new FlashSaleRuleConfig(FlashSaleRuleConfig.CURRENT_SCHEMA_VERSION, new BigDecimal("49.90"), 2);

        String json = mapper.toJson(config, CampaignType.FLASH_SALE, START, END);

        assertThat(json).contains("\"schema_version\":1").contains("\"ruleType\":\"FLASH_SALE\"");
    }

    @Test
    void giftAddonConfigRoundTripsThroughFromJson() {
        GiftAddonRuleConfig config =
                new GiftAddonRuleConfig(GiftAddonRuleConfig.CURRENT_SCHEMA_VERSION, "SKU-GIFT-1", 3);

        String json = mapper.toJson(config, CampaignType.GIFT_ADDON, START, END);
        GiftAddonRuleConfig read = (GiftAddonRuleConfig) mapper.fromJson(CampaignType.GIFT_ADDON, json);

        assertThat(read.schemaVersion()).isEqualTo(GiftAddonRuleConfig.CURRENT_SCHEMA_VERSION);
        assertThat(read.giftSku()).isEqualTo("SKU-GIFT-1");
        assertThat(read.quantity()).isEqualTo(3);
    }

    @Test
    void flashSaleConfigRoundTripsThroughFromJson() {
        FlashSaleRuleConfig config =
                new FlashSaleRuleConfig(FlashSaleRuleConfig.CURRENT_SCHEMA_VERSION, new BigDecimal("49.90"), 2);

        String json = mapper.toJson(config, CampaignType.FLASH_SALE, START, END);
        FlashSaleRuleConfig read = (FlashSaleRuleConfig) mapper.fromJson(CampaignType.FLASH_SALE, json);

        assertThat(read.schemaVersion()).isEqualTo(FlashSaleRuleConfig.CURRENT_SCHEMA_VERSION);
        assertThat(read.salePrice()).isEqualByComparingTo("49.90");
        assertThat(read.purchaseLimit()).isEqualTo(2);
    }

    // --- 不合理規則被拒絕 (含明確原因) ---

    @Test
    void negativeDiscountRejectedWithReason() {
        DiscountRuleConfig config = new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION,
                DiscountKind.AMOUNT,
                new BigDecimal("-1"),
                null,
                ThresholdMode.NONE,
                null);

        assertThatThrownBy(() -> mapper.toJson(config, CampaignType.DISCOUNT, START, END))
                .isInstanceOf(RuleConfigValidationException.class)
                .hasMessageContaining("discount amount must not be negative");
    }

    @Test
    void percentageOverHundredRejectedWithReason() {
        DiscountRuleConfig config = new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION,
                DiscountKind.PERCENTAGE,
                null,
                new BigDecimal("150"),
                ThresholdMode.NONE,
                null);

        assertThatThrownBy(() -> mapper.toJson(config, CampaignType.DISCOUNT, START, END))
                .isInstanceOf(RuleConfigValidationException.class)
                .hasMessageContaining("discount percentage must not exceed 100%");
    }

    @Test
    void amountKindWithExtraPercentageRejectedWithReason() {
        DiscountRuleConfig config = new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION,
                DiscountKind.AMOUNT,
                new BigDecimal("100"),
                new BigDecimal("10"),
                ThresholdMode.NONE,
                null);

        assertThatThrownBy(() -> mapper.toJson(config, CampaignType.DISCOUNT, START, END))
                .isInstanceOf(RuleConfigValidationException.class)
                .hasMessageContaining("discount percentage must be absent when kind is AMOUNT");
    }

    @Test
    void percentageKindWithExtraAmountRejectedWithReason() {
        DiscountRuleConfig config = new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION,
                DiscountKind.PERCENTAGE,
                new BigDecimal("50"),
                new BigDecimal("10"),
                ThresholdMode.NONE,
                null);

        assertThatThrownBy(() -> mapper.toJson(config, CampaignType.DISCOUNT, START, END))
                .isInstanceOf(RuleConfigValidationException.class)
                .hasMessageContaining("discount amount must be absent when kind is PERCENTAGE");
    }

    @Test
    void endAtBeforeStartAtRejectedWithReason() {
        DiscountRuleConfig config = new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION,
                DiscountKind.AMOUNT,
                new BigDecimal("100"),
                null,
                ThresholdMode.NONE,
                null);

        assertThatThrownBy(() -> mapper.toJson(config, CampaignType.DISCOUNT, END, START))
                .isInstanceOf(RuleConfigValidationException.class)
                .hasMessageContaining("campaign endAt must not be earlier than startAt");
    }

    // --- 滿額門檻設定 (無門檻 / 滿指定金額可用) ---

    @Test
    void noThresholdModePersistsAndPassesValidation() {
        DiscountRuleConfig config = new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION,
                DiscountKind.AMOUNT,
                new BigDecimal("100"),
                null,
                ThresholdMode.NONE,
                null);

        String json = mapper.toJson(config, CampaignType.DISCOUNT, START, END);
        DiscountRuleConfig read = (DiscountRuleConfig) mapper.fromJson(CampaignType.DISCOUNT, json);

        assertThat(read.thresholdMode()).isEqualTo(ThresholdMode.NONE);
        assertThat(read.minSpend()).isNull();
    }

    @Test
    void minSpendThresholdModePersistsConfiguredThresholdAndPassesValidation() {
        DiscountRuleConfig config = new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION,
                DiscountKind.PERCENTAGE,
                null,
                new BigDecimal("10"),
                ThresholdMode.MIN_SPEND,
                new BigDecimal("1000"));

        String json = mapper.toJson(config, CampaignType.DISCOUNT, START, END);
        DiscountRuleConfig read = (DiscountRuleConfig) mapper.fromJson(CampaignType.DISCOUNT, json);

        assertThat(read.thresholdMode()).isEqualTo(ThresholdMode.MIN_SPEND);
        assertThat(read.minSpend()).isEqualByComparingTo("1000");
    }

    @Test
    void blankOrNonObjectStoredJsonFailsAsServerSideCorruption() {
        // fromJson reads the persisted column, so malformed stored data is a server-side fault
        // (RuleConfigPersistenceException → 500), not a client validation error (→ 400).
        assertThatThrownBy(() -> mapper.fromJson(CampaignType.DISCOUNT, ""))
                .isInstanceOf(RuleConfigPersistenceException.class)
                .hasMessageContaining("rule_config JSON is not a valid object");

        assertThatThrownBy(() -> mapper.fromJson(CampaignType.DISCOUNT, "42"))
                .isInstanceOf(RuleConfigPersistenceException.class)
                .hasMessageContaining("rule_config JSON is not a valid object");
    }

    // --- 舊版 JSONB 向後相容讀取 ---

    @Test
    void readingOlderSchemaVersionUpcastsToCurrentDtoStructure() {
        // v1 discount JSON: predates the spend-threshold feature, so it has no thresholdMode.
        String v1Json = "{\"schema_version\":1,\"ruleType\":\"DISCOUNT\",\"kind\":\"AMOUNT\",\"amount\":50}";

        DiscountRuleConfig read = (DiscountRuleConfig) mapper.fromJson(CampaignType.DISCOUNT, v1Json);

        assertThat(read.schemaVersion()).isEqualTo(DiscountRuleConfig.CURRENT_SCHEMA_VERSION);
        assertThat(read.thresholdMode()).isEqualTo(ThresholdMode.NONE);
        assertThat(read.amount()).isEqualByComparingTo("50");
    }
}
