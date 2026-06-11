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
 * <p><strong>Exception isolation vs. publish propagation (spec §6 觸發判定例外隔離 + at-least-once §9).</strong>
 * The two failure classes are handled deliberately differently:
 *
 * <ul>
 *   <li><em>Trigger-determination failure</em> (a campaign's evaluator throwing) is isolated: the
 *       registry already turns an evaluator throw into a {@code SKIPPED} decision, and the scoped
 *       broad catch in {@link #decideEmission} is a belt-and-braces net, so one campaign's failed
 *       determination never stops the others. This is the "判定例外隔離" the spec requires.
 *   <li><em>Publish failure</em> (the broker rejecting / timing out a {@link ReachRequested}) is
 *       <strong>NOT</strong> swallowed: it propagates out of {@link #handle}. The {@link DomainEventConsumer}
 *       therefore does not acknowledge, the offset is not advanced, and the behavior event is
 *       re-delivered (at-least-once, design.md §9). Swallowing it would silently lose the matched
 *       reach and degrade the contract to at-most-once.
 * </ul>
 *
 * <p>On re-delivery a campaign whose publish already succeeded before another's failed is re-emitted,
 * so a single domain event can produce duplicate {@link ReachRequested}s. That is the intended
 * effectively-once behavior: the reach side dedups on {@code unique(campaign_id, send_cycle_key,
 * trigger_type)} (design.md §9), so duplicates collapse to one batch downstream.
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
     * for each that triggers. Per-campaign <em>determination</em> failures are isolated and skipped; a
     * <em>publish</em> failure propagates so the caller can decline to ack and trigger re-delivery
     * (at-least-once, design.md §9).
     *
     * @param event the deserialized inbound behavior event
     * @throws RuntimeException if publishing a matched {@link ReachRequested} fails (so the offset is not
     *     committed and the event is re-delivered)
     */
    public void handle(DomainBehaviorEvent event) {
        if (event == null) {
            LOG.warn("Cannot handle null behavior event");
            return;
        }
        List<Campaign> running = campaignRepository.findByStatus(CampaignStatus.RUNNING);
        for (Campaign campaign : running) {
            ReachRequested toEmit = decideEmission(campaign, event);
            if (toEmit == null) {
                continue;
            }
            // Publish OUTSIDE the isolation catch: a broker failure must propagate so DomainEventConsumer
            // skips the ack and the event is re-delivered. Swallowing it would silently drop the reach.
            publisher.publish(toEmit);
            LOG.info(
                    "Emitted ReachRequested for campaign {} on event {} sendCycle={}",
                    campaign.getId(),
                    event.eventId(),
                    toEmit.sendCycle());
        }
    }

    /**
     * Derives this campaign's trigger decision for the event and, on {@code TRIGGER}, builds the
     * activity-level {@link ReachRequested} to emit (or {@code null} if it does not trigger). A failed
     * <em>determination</em> for one campaign is isolated here (logged, returns {@code null}) so it does
     * not stop the rest of the batch — this is the "判定例外隔離" the spec requires. Publishing is the
     * caller's responsibility and is deliberately left outside this catch.
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // deliberate broad catch: per-campaign determination isolation
    private ReachRequested decideEmission(Campaign campaign, DomainBehaviorEvent event) {
        try {
            TriggerDecision decision =
                    triggerRegistry.evaluate(TriggerContext.event(campaign.getType(), event.eventType()));
            if (!decision.isTriggered()) {
                return null;
            }
            return new ReachRequested(
                    campaign.getId(),
                    campaign.getTargetSpec(),
                    campaign.getReachPlan(),
                    TriggerType.EVENT,
                    sendCycleKey(event.eventId()),
                    event.eventId());
        } catch (RuntimeException ex) {
            LOG.warn(
                    "Behavior-trigger determination failed for campaign {} on event {}: {}",
                    campaign.getId(),
                    event.eventId(),
                    ex.getMessage(),
                    ex);
            return null;
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
