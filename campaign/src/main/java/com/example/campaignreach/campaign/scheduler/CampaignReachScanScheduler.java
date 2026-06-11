package com.example.campaignreach.campaign.scheduler;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.campaign.evaluation.ReachTriggerEvaluatorRegistry;
import com.example.campaignreach.campaign.evaluation.TriggerContext;
import com.example.campaignreach.campaign.evaluation.TriggerDecision;
import com.example.campaignreach.campaign.publish.ReachRequestPublisher;
import com.example.campaignreach.shared.event.ReachRequested;
import com.example.campaignreach.shared.event.TriggerType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Path 1 of the reach-trigger flow (task 6.2, FR-008, US-004; {@code diagrams/01-sequence-reach-flow.puml}).
 * Every N minutes this sweep scans {@code status=RUNNING} campaigns and, for each whose schedule cycle
 * has come due, asks the {@link ReachTriggerEvaluatorRegistry} whether reach should fire; on a
 * {@code TRIGGER} decision it emits one activity-level {@link ReachRequested} to {@code reach.requested}
 * with {@code triggerType=SCHEDULED_BATCH} and a deterministic {@code sendCycle} key.
 *
 * <p><strong>Activity-level event.</strong> Per the sequence diagram the emitted event carries the
 * campaign's {@code targetSpec}/{@code reachPlan} JSON snapshots verbatim but <em>no recipient
 * list</em> — the reach orchestrator resolves and expands the audience itself.
 *
 * <p><strong>Deterministic dedup ({@code send_cycle_key}, §5, US-004).</strong> The key is
 * {@code sched:{campaignId}:{cycleStart}} where {@code cycleStart} is {@link Instant#now() now}
 * <em>floored</em> to a configurable cycle unit ({@code campaignreach.scheduler.reach-scan.cycle-duration},
 * default 1 hour) and rendered as an ISO-8601 string. Because the key derives from the floored cycle
 * boundary — never from {@code now()} itself or scheduler-start time — every instance, restart, and
 * back-scan within the same logical cycle derives the <em>same</em> key. Combined with the
 * {@link SchedulerLock} on this method, the same activity + same cycle fires exactly once: not missed,
 * not duplicated. (Finer per-campaign cycle granularity is a future extension — there is no
 * per-campaign granularity field in {@code reachPlan} yet, so MVP uses one global cycle unit.)
 *
 * <p><strong>Due rule (MVP, deterministic).</strong> A {@code RUNNING} campaign is "due" for the
 * current cycle when the cycle boundary falls within its active window {@code [startAt, endAt)}. This
 * keeps the rule simple and reproducible; the registry's {@code SCHEDULED} evaluator then makes the
 * final judgement from {@link TriggerContext#due()}.
 *
 * <p><strong>Per-campaign isolation.</strong> The registry already isolates evaluator throws as
 * {@code SKIPPED}; the publish/derivation per campaign is additionally guarded by a scoped broad catch
 * (per the {@code IllegalCatch} convention in CLAUDE.md) so one campaign's failure never aborts the
 * sweep.
 */
@Component
public class CampaignReachScanScheduler {

    /** ShedLock lock name; one logical lock guards the whole RUNNING-campaign sweep per cycle. */
    static final String LOCK_NAME = "campaign-reach-scan";

    private static final Logger LOG = LoggerFactory.getLogger(CampaignReachScanScheduler.class);

    private final CampaignRepository campaignRepository;
    private final ReachTriggerEvaluatorRegistry triggerRegistry;
    private final ReachRequestPublisher publisher;
    private final Duration cycleDuration;

    /**
     * @param campaignRepository source of {@code RUNNING} campaigns to scan
     * @param triggerRegistry resolves the per-campaign {@link TriggerDecision}
     * @param publisher emits the activity-level {@link ReachRequested}
     * @param cycleDuration the schedule-cycle unit {@code now} is floored to for the deterministic key
     */
    @SuppressFBWarnings(
            value = "CT_CONSTRUCTOR_THROW",
            justification = "Spring @Component singleton; no finalizer defined, so the finalizer-attack "
                    + "vector does not apply. The throw is an intentional fail-fast config guard.")
    public CampaignReachScanScheduler(
            CampaignRepository campaignRepository,
            ReachTriggerEvaluatorRegistry triggerRegistry,
            ReachRequestPublisher publisher,
            @Value("${campaignreach.scheduler.reach-scan.cycle-duration:PT1H}") Duration cycleDuration) {
        if (cycleDuration == null || cycleDuration.isNegative() || cycleDuration.isZero()) {
            throw new IllegalArgumentException("cycleDuration must be a positive non-zero duration");
        }
        this.campaignRepository = campaignRepository;
        this.triggerRegistry = triggerRegistry;
        this.publisher = publisher;
        this.cycleDuration = cycleDuration;
    }

    /**
     * One reach-scan sweep: scan {@code RUNNING} campaigns and emit {@link ReachRequested} for those
     * whose cycle is due and trigger. {@link SchedulerLock} ensures only one instance runs a given
     * cycle; {@code lockAtMostFor} bounds the hold if a node dies mid-sweep.
     */
    @Scheduled(fixedDelayString = "${campaignreach.scheduler.reach-scan.fixed-delay-ms:60000}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT9M", lockAtLeastFor = "PT1S")
    public void scanAndEmit() {
        Instant now = Instant.now();
        Instant cycleStart = floorToCycle(now);
        List<Campaign> running = campaignRepository.findByStatus(CampaignStatus.RUNNING);
        for (Campaign campaign : running) {
            evaluateAndEmit(campaign, cycleStart);
        }
    }

    /**
     * Derives this campaign's trigger decision for the cycle and, on {@code TRIGGER}, publishes the
     * activity-level {@link ReachRequested}. Guarded so a single campaign's failure does not abort the
     * sweep (the registry isolates evaluator throws; this isolates derivation/publish failures).
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // deliberate broad catch: per-campaign exception isolation
    private void evaluateAndEmit(Campaign campaign, Instant cycleStart) {
        try {
            boolean due = isDue(campaign, cycleStart);
            TriggerDecision decision = triggerRegistry.evaluate(TriggerContext.scheduled(campaign.getType(), due));
            if (!decision.isTriggered()) {
                return;
            }
            String sendCycle = sendCycleKey(campaign.getId(), cycleStart);
            ReachRequested event = new ReachRequested(
                    campaign.getId(),
                    campaign.getTargetSpec(),
                    campaign.getReachPlan(),
                    TriggerType.SCHEDULED_BATCH,
                    sendCycle,
                    null);
            publisher.publish(event);
            LOG.info("Emitted ReachRequested for campaign {} sendCycle={}", campaign.getId(), sendCycle);
        } catch (RuntimeException ex) {
            LOG.warn("Reach-scan failed for campaign {}: {}", campaign.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * Whether the campaign's schedule cycle is due for {@code cycleStart}: the campaign is RUNNING and
     * the cycle boundary lies within its active window {@code [startAt, endAt)} (MVP rule, deterministic).
     */
    private boolean isDue(Campaign campaign, Instant cycleStart) {
        return !cycleStart.isBefore(campaign.getStartAt()) && cycleStart.isBefore(campaign.getEndAt());
    }

    /**
     * Floors an instant to the configured cycle unit so the same logical cycle always maps to the same
     * boundary (the heart of the deterministic dedup key). Floors by epoch-millis modulo the cycle
     * length — robust for any cycle duration, including those finer or coarser than a day.
     */
    Instant floorToCycle(Instant instant) {
        long cycleMillis = cycleDuration.toMillis();
        long epochMillis = instant.toEpochMilli();
        return Instant.ofEpochMilli(epochMillis - Math.floorMod(epochMillis, cycleMillis));
    }

    /**
     * Builds the deterministic {@code send_cycle_key} for a SCHEDULED_BATCH trigger:
     * {@code sched:{campaignId}:{cycleStart}} with {@code cycleStart} as an ISO-8601 string. Same
     * campaign + same floored cycle ⇒ identical key (US-004).
     */
    static String sendCycleKey(java.util.UUID campaignId, Instant cycleStart) {
        return "sched:" + campaignId + ":" + cycleStart.toString();
    }
}
