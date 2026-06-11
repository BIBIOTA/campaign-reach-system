package com.example.campaignreach.reach.dispatcher;

import com.example.campaignreach.shared.event.CampaignStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reach-side reaction to campaign lifecycle deactivation. The shared event keeps the module boundary
 * clean: campaign publishes a stable contract, and reach owns the {@code reach_task} update.
 *
 * <p><strong>Transaction contract (do not weaken):</strong> this is a plain synchronous {@link
 * EventListener}, not a {@code @TransactionalEventListener} and not {@code @Async}. The publisher
 * ({@code CampaignApplicationService.transition}) fires the event from inside its {@code
 * @Transactional} method <em>before</em> commit, so this handler runs on the same thread and joins
 * the publisher's transaction — the campaign status change and the {@code reach_task} cancellation
 * commit atomically. That atomicity, plus the {@code reach_task} row locks taken by {@link
 * ReachTaskDispatchDao#cancelPendingForCampaign}, is what serializes cancel-vs-dispatch: a task the
 * dispatcher already claimed (PROCESSING) is skipped here and allowed to finish (the bounded,
 * explicitly-accepted leak window). Switching this to {@code @Async}, to {@code AFTER_COMMIT}, or
 * giving reach a separate {@code DataSource}/transaction manager would break that guarantee — the
 * status change could commit while cancellation runs in (or after) a different transaction, opening
 * a wider race. The end-to-end guarantee is pinned by {@code
 * CampaignDeactivationCancelsReachTasksIntegrationTest}.
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
