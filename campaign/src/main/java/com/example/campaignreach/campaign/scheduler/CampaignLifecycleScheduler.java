package com.example.campaignreach.campaign.scheduler;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.shared.event.CampaignStatusChanged;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
 * <p><b>Per-campaign isolation.</b> The sweep itself is <em>not</em> wrapped in a single
 * transaction. Instead each campaign's {@code transitionTo} + {@code saveAndFlush} runs in its own
 * transaction via {@link TransactionTemplate}, and the broad catch wraps that whole boundary. This
 * makes the isolation real: a concurrent operator edit that loses the optimistic-lock race raises
 * {@code ObjectOptimisticLockingFailureException} on {@code saveAndFlush} (see
 * {@link CampaignRepository}), which rolls back only that one campaign's transaction and is caught
 * here — the remaining campaigns are unaffected. (Contrast {@code ReachTriggerEvaluatorRegistry},
 * whose broad catch wraps pure in-memory evaluation; a write inside a shared transaction would not
 * be equivalently isolated, which is why each campaign needs its own boundary.)
 *
 * <p>Scope note: this scheduler only advances lifecycle status timing. It emits no reach-request
 * Kafka events and scans no send cycles — those are separate tasks.
 */
@Component
public class CampaignLifecycleScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignLifecycleScheduler.class);

    private final CampaignRepository campaignRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /**
     * Wires the lifecycle repository, cross-module event publisher, and transaction manager used for
     * per-campaign isolated transitions.
     */
    public CampaignLifecycleScheduler(
            CampaignRepository campaignRepository,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.campaignRepository = campaignRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * One lifecycle sweep: auto-start due {@code SCHEDULED} campaigns, then auto-end due
     * {@code RUNNING}/{@code PAUSED} campaigns. The sweep is deliberately non-transactional; each
     * campaign is advanced in its own transaction (see {@link #transition}) so one campaign's
     * optimistic-lock loss is isolated and the sweep continues.
     */
    @Scheduled(fixedDelayString = "${campaignreach.scheduler.lifecycle.fixed-delay-ms:60000}")
    @SchedulerLock(name = "campaign-lifecycle-advance", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1S")
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
     * Applies one guarded transition and persists it in its own transaction, isolating any
     * per-campaign failure so the sweep continues. The {@code saveAndFlush} forces the write — and
     * any {@code ObjectOptimisticLockingFailureException} from a concurrent edit — to surface inside
     * this boundary (and this catch), rather than at an outer commit.
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // deliberate broad catch: per-campaign exception isolation
    private void transition(Campaign campaign, CampaignStatus target) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                CampaignStatus previousStatus = campaign.getStatus();
                campaign.transitionTo(target);
                campaignRepository.saveAndFlush(campaign);
                eventPublisher.publishEvent(new CampaignStatusChanged(
                        campaign.getId(), previousStatus.name(), target.name(), Instant.now()));
            });
            LOG.info("Auto-advanced campaign {} to {}", campaign.getId(), target);
        } catch (RuntimeException ex) {
            LOG.warn("Failed to auto-advance campaign {} to {}: {}", campaign.getId(), target, ex.getMessage(), ex);
        }
    }
}
