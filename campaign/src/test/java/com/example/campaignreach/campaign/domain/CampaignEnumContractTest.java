package com.example.campaignreach.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Fast (no-DB) guard on the Campaign enum contracts (spec §4). The full
 * round-trip-through-PostgreSQL persistence of these values lives in the
 * Testcontainers test in {@code :app} (auto-skipped without Docker).
 */
class CampaignEnumContractTest {

    /** Spec §4: status enum is exactly the five lifecycle values. */
    @Test
    void statusEnumHasExactlyTheFiveSpecValues() {
        assertThat(CampaignStatus.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("DRAFT", "SCHEDULED", "RUNNING", "PAUSED", "ENDED");
    }

    /** Spec §4 / ER campaign_type: type enum is exactly the three offer types. */
    @Test
    void typeEnumHasExactlyTheThreeSpecValues() {
        assertThat(CampaignType.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("DISCOUNT", "GIFT_ADDON", "FLASH_SALE");
    }

    /** The aggregate exposes the constructor-supplied state via its getters. */
    @Test
    void aggregateExposesConstructorState() {
        java.time.Instant start = java.time.Instant.parse("2026-01-01T00:00:00Z");
        Campaign campaign = new Campaign(
                java.util.UUID.randomUUID(),
                "Promo",
                CampaignType.FLASH_SALE,
                CampaignStatus.SCHEDULED,
                start,
                start.plusSeconds(3600),
                "{}",
                "{}",
                "{}");

        assertThat(campaign.getName()).isEqualTo("Promo");
        assertThat(campaign.getType()).isEqualTo(CampaignType.FLASH_SALE);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(campaign.getStartAt()).isEqualTo(start);
        assertThat(campaign.getRuleConfig()).isEqualTo("{}");
    }
}
