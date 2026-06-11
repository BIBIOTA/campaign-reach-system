package com.example.campaignreach.reach.dispatcher;

import com.example.campaignreach.shared.event.CampaignStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reach-side reaction to campaign lifecycle deactivation. The shared event keeps the module boundary
 * clean: campaign publishes a stable contract, and reach owns the {@code reach_task} update.
 */
@Component
public class CampaignStatusChangeListener {

    private static final String PAUSED = "PAUSED";
    private static final String ENDED = "ENDED";

    private final ReachTaskDispatchDao dispatchDao;

    public CampaignStatusChangeListener(ReachTaskDispatchDao dispatchDao) {
        this.dispatchDao = dispatchDao;
    }

    /** Cancels only PENDING / RETRY_SCHEDULED tasks when a campaign becomes PAUSED or ENDED. */
    @EventListener
    public void onCampaignStatusChanged(CampaignStatusChanged event) {
        if (PAUSED.equals(event.newStatus()) || ENDED.equals(event.newStatus())) {
            dispatchDao.cancelPendingForCampaign(event.campaignId());
        }
    }
}
