package com.example.campaignreach.campaign.scheduler;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives time-based campaign lifecycle transitions (task 6.1, FR-012, US-002): when a campaign
 * reaches its {@code startAt} it auto-advances {@code SCHEDULED → RUNNING}, and when it reaches its
 * {@code endAt} it auto-advances {@code RUNNING/PAUSED → ENDED}.
 *
 * <p>Every status change routes through {@link Campaign#transitionTo(CampaignStatus)}, so only legal
 * lifecycle edges (per {@code diagrams/02-state-campaign-and-task-lifecycle.puml}) are ever applied;
 * the scheduler never sets status directly. A single {@link Instant#now() now} is captured per tick
 * for a consistent view. The start sweep runs before the end sweep, so a {@code SCHEDULED} campaign
 * whose {@code startAt} and {@code endAt} are both already past converges
 * {@code SCHEDULED → RUNNING → ENDED} within one tick.
 *
 * <p>Per-campaign failures are isolated (the established repo convention; see {@code
 * ReachTriggerEvaluatorRegistry} and CLAUDE.md): one bad campaign is logged and skipped so it does
 * not abort the rest of the sweep.
 *
 * <p>Scope note: this scheduler only advances lifecycle status timing. It emits no Kafka events and
 * scans no send cycles — those are separate tasks.
 */
@Component
public class CampaignLifecycleScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignLifecycleScheduler.class);

    private final CampaignRepository campaignRepository;

    public CampaignLifecycleScheduler(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    /**
     * One lifecycle sweep: auto-start due {@code SCHEDULED} campaigns, then auto-end due
     * {@code RUNNING}/{@code PAUSED} campaigns. Transactional so the whole tick commits atomically and
     * each {@code save} flows through the optimistic-lock version.
     */
    @Scheduled(fixedDelayString = "${campaignreach.scheduler.lifecycle.fixed-delay-ms:60000}")
    @Transactional
    public void advanceLifecycle() {
        Instant now = Instant.now();
        autoStart(now);
        autoEnd(now);
    }

    /** Advances {@code SCHEDULED} campaigns whose {@code startAt <= now} to {@code RUNNING}. */
    private void autoStart(Instant now) {
        List<Campaign> due = campaignRepository.findByStatusAndStartAtLessThanEqual(CampaignStatus.SCHEDULED, now);
        for (Campaign campaign : due) {
            transition(campaign, CampaignStatus.RUNNING);
        }
    }

    /** Advances {@code RUNNING}/{@code PAUSED} campaigns whose {@code endAt <= now} to {@code ENDED}. */
    private void autoEnd(Instant now) {
        List<Campaign> due = campaignRepository.findByStatusInAndEndAtLessThanEqual(
                List.of(CampaignStatus.RUNNING, CampaignStatus.PAUSED), now);
        for (Campaign campaign : due) {
            transition(campaign, CampaignStatus.ENDED);
        }
    }

    /**
     * Applies one guarded transition and persists it, isolating any per-campaign failure so the sweep
     * continues.
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // deliberate broad catch: per-campaign exception isolation
    private void transition(Campaign campaign, CampaignStatus target) {
        try {
            campaign.transitionTo(target);
            campaignRepository.save(campaign);
            LOG.info("Auto-advanced campaign {} to {}", campaign.getId(), target);
        } catch (RuntimeException ex) {
            LOG.warn("Failed to auto-advance campaign {} to {}: {}", campaign.getId(), target, ex.getMessage(), ex);
        }
    }
}
