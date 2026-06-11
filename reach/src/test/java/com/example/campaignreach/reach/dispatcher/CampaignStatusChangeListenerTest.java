package com.example.campaignreach.reach.dispatcher;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.campaignreach.shared.event.CampaignStatusChanged;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for reach-side reaction to campaign lifecycle events (Task 10.1). */
@ExtendWith(MockitoExtension.class)
class CampaignStatusChangeListenerTest {

    @Mock
    private ReachTaskDispatchDao dispatchDao;

    @Test
    void pausedCampaignCancelsPendingTasks() {
        UUID campaignId = UUID.randomUUID();
        CampaignStatusChangeListener listener = new CampaignStatusChangeListener(dispatchDao);

        listener.onCampaignStatusChanged(
                new CampaignStatusChanged(campaignId, "RUNNING", "PAUSED", Instant.parse("2026-06-09T10:00:00Z")));

        verify(dispatchDao).cancelPendingForCampaign(campaignId);
    }

    @Test
    void endedCampaignCancelsPendingTasks() {
        UUID campaignId = UUID.randomUUID();
        CampaignStatusChangeListener listener = new CampaignStatusChangeListener(dispatchDao);

        listener.onCampaignStatusChanged(
                new CampaignStatusChanged(campaignId, "RUNNING", "ENDED", Instant.parse("2026-06-09T10:00:00Z")));

        verify(dispatchDao).cancelPendingForCampaign(campaignId);
    }

    @Test
    void activeCampaignStatusDoesNotCancelTasks() {
        UUID campaignId = UUID.randomUUID();
        CampaignStatusChangeListener listener = new CampaignStatusChangeListener(dispatchDao);

        listener.onCampaignStatusChanged(
                new CampaignStatusChanged(campaignId, "PAUSED", "RUNNING", Instant.parse("2026-06-09T10:00:00Z")));

        verify(dispatchDao, never()).cancelPendingForCampaign(campaignId);
    }
}
