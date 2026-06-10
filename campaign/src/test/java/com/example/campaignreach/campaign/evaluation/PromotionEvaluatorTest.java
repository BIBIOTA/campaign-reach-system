package com.example.campaignreach.campaign.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.example.campaignreach.campaign.domain.rule.DiscountKind;
import com.example.campaignreach.campaign.domain.rule.DiscountRuleConfig;
import com.example.campaignreach.campaign.domain.rule.FlashSaleRuleConfig;
import com.example.campaignreach.campaign.domain.rule.GiftAddonRuleConfig;
import com.example.campaignreach.campaign.domain.rule.ThresholdMode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Fast (no-DB) unit tests for the PromotionEvaluator strategy (spec §4 / FR-002 / FR-019). Test
 * names map to the spec scenarios.
 */
class PromotionEvaluatorTest {

    private final DiscountPromotionEvaluator discountEvaluator = new DiscountPromotionEvaluator();
    private final FlashSalePromotionEvaluator flashSaleEvaluator = new FlashSalePromotionEvaluator();

    private static DiscountRuleConfig amount(BigDecimal off, ThresholdMode mode, BigDecimal minSpend) {
        return new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION, DiscountKind.AMOUNT, off, null, mode, minSpend);
    }

    private static DiscountRuleConfig percentage(BigDecimal pct, ThresholdMode mode, BigDecimal minSpend) {
        return new DiscountRuleConfig(
                DiscountRuleConfig.CURRENT_SCHEMA_VERSION, DiscountKind.PERCENTAGE, null, pct, mode, minSpend);
    }

    // --- 結帳時計算折扣 ---

    @Nested
    class 結帳時計算折扣 {

        @Test
        void amountDiscountAppliesFixedAmountOff() {
            CartContext ctx =
                    new CartContext(new BigDecimal("500"), amount(new BigDecimal("100"), ThresholdMode.NONE, null));

            PromotionResult result = discountEvaluator.evaluate(ctx);

            assertThat(result.applicable()).isTrue();
            assertThat(result.discountAmount()).isEqualByComparingTo("100");
        }

        @Test
        void amountDiscountIsCappedAtSubtotalSoResultIsNeverNegative() {
            CartContext ctx =
                    new CartContext(new BigDecimal("80"), amount(new BigDecimal("100"), ThresholdMode.NONE, null));

            PromotionResult result = discountEvaluator.evaluate(ctx);

            assertThat(result.applicable()).isTrue();
            assertThat(result.discountAmount()).isEqualByComparingTo("80");
        }

        @Test
        void percentageDiscountComputesSubtotalTimesPercentRoundedHalfUp() {
            CartContext ctx =
                    new CartContext(new BigDecimal("333"), percentage(new BigDecimal("10"), ThresholdMode.NONE, null));

            PromotionResult result = discountEvaluator.evaluate(ctx);

            assertThat(result.applicable()).isTrue();
            assertThat(result.discountAmount()).isEqualByComparingTo("33.30");
        }

        @Test
        void minSpendThresholdMetAppliesDiscount() {
            CartContext ctx = new CartContext(
                    new BigDecimal("500"),
                    amount(new BigDecimal("50"), ThresholdMode.MIN_SPEND, new BigDecimal("300")));

            PromotionResult result = discountEvaluator.evaluate(ctx);

            assertThat(result.applicable()).isTrue();
            assertThat(result.discountAmount()).isEqualByComparingTo("50");
        }

        @Test
        void minSpendThresholdExactlyMetAppliesDiscount() {
            // Boundary: subtotal == minSpend is inclusive (the gate is subtotal < minSpend).
            CartContext ctx = new CartContext(
                    new BigDecimal("300"),
                    amount(new BigDecimal("50"), ThresholdMode.MIN_SPEND, new BigDecimal("300")));

            PromotionResult result = discountEvaluator.evaluate(ctx);

            assertThat(result.applicable()).isTrue();
            assertThat(result.discountAmount()).isEqualByComparingTo("50");
        }

        @Test
        void percentageDiscountRoundsHalfUpAtTheHalfwayBoundary() {
            // 333.35 × 10% = 33.335 → HALF_UP at the 3rd decimal rounds the .5 up to 33.34.
            CartContext ctx = new CartContext(
                    new BigDecimal("333.35"), percentage(new BigDecimal("10"), ThresholdMode.NONE, null));

            PromotionResult result = discountEvaluator.evaluate(ctx);

            assertThat(result.applicable()).isTrue();
            assertThat(result.discountAmount()).isEqualByComparingTo("33.34");
        }

        @Test
        void minSpendThresholdNotMetReturnsNotApplicable() {
            CartContext ctx = new CartContext(
                    new BigDecimal("200"),
                    amount(new BigDecimal("50"), ThresholdMode.MIN_SPEND, new BigDecimal("300")));

            PromotionResult result = discountEvaluator.evaluate(ctx);

            assertThat(result.applicable()).isFalse();
            assertThat(result.discountAmount()).isEqualByComparingTo("0");
        }
    }

    // --- 閃購擴充點之邊界降級（MVP stub 級） ---

    @Nested
    class 閃購擴充點之邊界降級 {

        @Test
        void flashSaleStubReturnsNotApplicableWithoutThrowing() {
            CartContext ctx = new CartContext(
                    new BigDecimal("199"),
                    new FlashSaleRuleConfig(FlashSaleRuleConfig.CURRENT_SCHEMA_VERSION, new BigDecimal("49.90"), 2));

            PromotionResult result = flashSaleEvaluator.evaluate(ctx);

            assertThat(result.applicable()).isFalse();
            assertThat(result.discountAmount()).isEqualByComparingTo("0");
        }
    }

    // --- 新增活動類型不動既有程式 ---

    @Nested
    class 新增活動類型不動既有程式 {

        private final PromotionEvaluatorRegistry registry =
                new PromotionEvaluatorRegistry(List.of(discountEvaluator, flashSaleEvaluator));

        @Test
        void registryResolvesEvaluatorBySupportsType() {
            assertThat(registry.find(CampaignType.DISCOUNT)).containsSame(discountEvaluator);
            assertThat(registry.find(CampaignType.FLASH_SALE)).containsSame(flashSaleEvaluator);
        }

        @Test
        void registryDispatchesToMatchingEvaluatorByContextType() {
            CartContext ctx =
                    new CartContext(new BigDecimal("500"), amount(new BigDecimal("100"), ThresholdMode.NONE, null));

            PromotionResult result = registry.evaluate(ctx);

            assertThat(result.applicable()).isTrue();
            assertThat(result.discountAmount()).isEqualByComparingTo("100");
        }

        @Test
        void registryReturnsNotApplicableForUnregisteredType() {
            // GIFT_ADDON intentionally has no PromotionEvaluator in MVP; registry must not break.
            assertThat(registry.find(CampaignType.GIFT_ADDON)).isEmpty();
        }

        @Test
        void registryEvaluateReturnsNotApplicableForUnregisteredType() {
            // End-to-end: dispatching a GIFT_ADDON context falls back to notApplicable() rather than throwing.
            CartContext ctx = new CartContext(
                    new BigDecimal("199"),
                    new GiftAddonRuleConfig(GiftAddonRuleConfig.CURRENT_SCHEMA_VERSION, "GIFT-SKU-1", 1));

            PromotionResult result = registry.evaluate(ctx);

            assertThat(result.applicable()).isFalse();
            assertThat(result.discountAmount()).isEqualByComparingTo("0");
        }
    }
}
