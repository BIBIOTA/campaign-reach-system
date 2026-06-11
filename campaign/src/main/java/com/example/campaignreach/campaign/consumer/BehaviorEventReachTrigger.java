package com.example.campaignreach.campaign.consumer;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.campaign.evaluation.ReachTriggerEvaluatorRegistry;
import com.example.campaignreach.campaign.evaluation.TriggerContext;
import com.example.campaignreach.campaign.evaluation.TriggerDecision;
import com.example.campaignreach.campaign.publish.ReachRequestPublisher;
import com.example.campaignreach.shared.event.ReachRequested;
import com.example.campaignreach.shared.event.TriggerType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Path 2 of the reach-trigger flow (task 6.3, FR-008, US-005; {@code diagrams/01-sequence-reach-flow.puml}).
 * Pure handling logic for a single inbound {@link DomainBehaviorEvent}: it scans {@code status=RUNNING}
 * campaigns and, for each whose {@link ReachTriggerEvaluatorRegistry} judges the behavior event a
 * {@code TRIGGER}, emits one activity-level {@link ReachRequested} to {@code reach.requested} with
 * {@code triggerType=EVENT} and {@code sendCycle=event:{triggerEventId}}.
 *
 * <p><strong>Convergence with path 1.</strong> It publishes through the same
 * {@link ReachRequestPublisher} the scheduled scan (task 6.2) uses, so both trigger paths converge on
 * the identical event type, topic and partition keying — giving consistent downstream tracking
 * ("下游追蹤方式一致", US-005).
 *
 * <p><strong>Activity-level event.</strong> Per the sequence diagram the emitted event carries the
 * campaign's {@code targetSpec}/{@code reachPlan} JSON snapshots verbatim but <em>no recipient
 * list</em> — the reach orchestrator resolves and expands the audience itself.
 *
 * <p><strong>send_cycle_key (§5).</strong> For an EVENT trigger the key is {@code event:{triggerEventId}}
 * where {@code triggerEventId} is the source event's unique id — the same value persisted as
 * {@code send_cycle_key}. Different event ids do not dedup against each other; cross-event duplicate
 * handling is frequency-capping in reach, out of scope here.
 *
 * <p><strong>Exception isolation (spec §6 觸發判定例外隔離).</strong> The registry already isolates an
 * evaluator throw as a {@code SKIPPED} decision (recorded with a reason, never propagated), so a
 * single campaign's failed determination does not stop the others. The publish per campaign is
 * additionally guarded by a scoped broad catch (per the {@code IllegalCatch} convention in CLAUDE.md)
 * so one campaign's publish failure never aborts emission for the rest of the batch.
 *
 * <p>This class is deliberately Kafka-free so it can be unit-tested without a broker; the
 * {@link DomainEventConsumer} is the thin {@code @KafkaListener} adapter that calls it then acks.
 */
@Component
public class BehaviorEventReachTrigger {

    private static final Logger LOG = LoggerFactory.getLogger(BehaviorEventReachTrigger.class);

    private final CampaignRepository campaignRepository;
    private final ReachTriggerEvaluatorRegistry triggerRegistry;
    private final ReachRequestPublisher publisher;

    /**
     * @param campaignRepository source of {@code RUNNING} campaigns to match the event against
     * @param triggerRegistry resolves the per-campaign {@link TriggerDecision}, with built-in isolation
     * @param publisher emits the activity-level {@link ReachRequested}
     */
    public BehaviorEventReachTrigger(
            CampaignRepository campaignRepository,
            ReachTriggerEvaluatorRegistry triggerRegistry,
            ReachRequestPublisher publisher) {
        this.campaignRepository = campaignRepository;
        this.triggerRegistry = triggerRegistry;
        this.publisher = publisher;
    }

    /**
     * Matches one behavior event against all {@code RUNNING} campaigns and emits a {@link ReachRequested}
     * for each that triggers. Safe to call without Kafka; throws nothing for per-campaign failures
     * (registry isolates evaluator throws, the scoped catch isolates publish failures).
     *
     * @param event the deserialized inbound behavior event
     */
    public void handle(DomainBehaviorEvent event) {
        if (event == null) {
            LOG.warn("Cannot handle null behavior event");
            return;
        }
        List<Campaign> running = campaignRepository.findByStatus(CampaignStatus.RUNNING);
        for (Campaign campaign : running) {
            evaluateAndEmit(campaign, event);
        }
    }

    /**
     * Derives this campaign's trigger decision for the event and, on {@code TRIGGER}, publishes the
     * activity-level {@link ReachRequested}. Guarded so a single campaign's publish failure does not
     * abort the rest of the batch (the registry isolates evaluator throws; this isolates publish failures).
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // deliberate broad catch: per-campaign exception isolation
    private void evaluateAndEmit(Campaign campaign, DomainBehaviorEvent event) {
        try {
            TriggerDecision decision =
                    triggerRegistry.evaluate(TriggerContext.event(campaign.getType(), event.eventType()));
            if (!decision.isTriggered()) {
                return;
            }
            String sendCycle = sendCycleKey(event.eventId());
            ReachRequested reachRequested = new ReachRequested(
                    campaign.getId(),
                    campaign.getTargetSpec(),
                    campaign.getReachPlan(),
                    TriggerType.EVENT,
                    sendCycle,
                    event.eventId());
            publisher.publish(reachRequested);
            LOG.info(
                    "Emitted ReachRequested for campaign {} on event {} sendCycle={}",
                    campaign.getId(),
                    event.eventId(),
                    sendCycle);
        } catch (RuntimeException ex) {
            LOG.warn(
                    "Behavior-trigger emit failed for campaign {} on event {}: {}",
                    campaign.getId(),
                    event.eventId(),
                    ex.getMessage(),
                    ex);
        }
    }

    /**
     * Builds the deterministic {@code send_cycle_key} for an EVENT trigger: {@code event:{triggerEventId}}
     * (§5). Same source event id ⇒ identical key; different event ids do not dedup against each other.
     */
    static String sendCycleKey(String triggerEventId) {
        return "event:" + triggerEventId;
    }
}
