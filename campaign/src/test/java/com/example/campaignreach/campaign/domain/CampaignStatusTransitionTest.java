package com.example.campaignreach.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Fast (no-Spring) guard on {@link Campaign#transitionTo} encoding the legal lifecycle edges
 * (FR-011), per {@code diagrams/02-state-campaign-and-task-lifecycle.puml}.
 */
class CampaignStatusTransitionTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private static Campaign campaignIn(CampaignStatus status) {
        return new Campaign(
                UUID.randomUUID(),
                "Promo",
                CampaignType.DISCOUNT,
                status,
                START,
                START.plusSeconds(3600),
                "{}",
                "{}",
                "{}");
    }

    @Test
    void legalEdgesAreAccepted() {
        Campaign campaign = campaignIn(CampaignStatus.DRAFT);

        campaign.transitionTo(CampaignStatus.SCHEDULED);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);

        campaign.transitionTo(CampaignStatus.RUNNING);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.RUNNING);

        campaign.transitionTo(CampaignStatus.PAUSED);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.PAUSED);

        campaign.transitionTo(CampaignStatus.RUNNING);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.RUNNING);

        campaign.transitionTo(CampaignStatus.ENDED);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ENDED);
    }

    @Test
    void illegalEdgeFromTerminalEndedIsRejected() {
        Campaign campaign = campaignIn(CampaignStatus.ENDED);

        assertThatThrownBy(() -> campaign.transitionTo(CampaignStatus.RUNNING))
                .isInstanceOf(IllegalCampaignStatusTransitionException.class)
                .hasMessageContaining("ENDED")
                .hasMessageContaining("RUNNING");
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ENDED);
    }

    @Test
    void skipAheadAndSelfTransitionAreRejected() {
        assertThatThrownBy(() -> campaignIn(CampaignStatus.DRAFT).transitionTo(CampaignStatus.RUNNING))
                .isInstanceOf(IllegalCampaignStatusTransitionException.class);
        assertThatThrownBy(() -> campaignIn(CampaignStatus.SCHEDULED).transitionTo(CampaignStatus.PAUSED))
                .isInstanceOf(IllegalCampaignStatusTransitionException.class);
        assertThatThrownBy(() -> campaignIn(CampaignStatus.RUNNING).transitionTo(CampaignStatus.RUNNING))
                .isInstanceOf(IllegalCampaignStatusTransitionException.class);
    }
}
