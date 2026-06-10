package com.example.campaignreach.shared.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Activity-level reach request — emitted once per campaign trigger to the {@code reach.requested}
 * topic (design.md §5). Both trigger paths (API/Scheduler and behavior event) converge on this same
 * event type and topic, giving consistent downstream tracking (FR-008).
 *
 * <p>This is an <strong>activity-level</strong> contract: it deliberately carries the
 * {@code targetSpec} / {@code reachPlan} so the reach orchestrator can resolve and expand the
 * audience itself — it MUST NOT carry the full recipient list.
 *
 * <p>Naming alignment (key acceptance, design.md §5): the wire/domain field is camelCase
 * {@code sendCycle} (a single string); the persisted column is snake_case {@code send_cycle_key}.
 * They are the <em>same value</em> — only the naming style differs. The orchestrator writes the
 * event's {@code sendCycle} straight into {@code send_cycle_key} with no transformation. Concrete
 * forms: {@code "sched:{campaignId}:{cycleStart}"} for {@link TriggerType#SCHEDULED_BATCH} and
 * {@code "event:{triggerEventId}"} for {@link TriggerType#EVENT}.
 *
 * @param campaignId owning campaign (ER {@code reach_request.campaign_id})
 * @param targetSpec audience selection condition snapshot (ER {@code target_spec_snapshot}); JSON
 *     payload kept as a string so the orchestrator can snapshot it verbatim
 * @param reachPlan channel/template/timing plan snapshot (ER {@code reach_plan_snapshot})
 * @param triggerType the source that caused the trigger (ER {@code trigger_type})
 * @param sendCycle send-cycle key; same value as the persisted {@code send_cycle_key}
 * @param triggerEventId source event id, present only for {@link TriggerType#EVENT} triggers (ER
 *     {@code reach_request.trigger_event_id}); {@code null} for scheduled batches
 */
public record ReachRequested(
        UUID campaignId,
        String targetSpec,
        String reachPlan,
        TriggerType triggerType,
        String sendCycle,
        String triggerEventId) {

    /** Validates required fields at construction time so invalid events fail before being serialized. */
    public ReachRequested {
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        if (targetSpec == null || targetSpec.isBlank()) {
            throw new IllegalArgumentException("targetSpec must not be blank");
        }
        if (reachPlan == null || reachPlan.isBlank()) {
            throw new IllegalArgumentException("reachPlan must not be blank");
        }
        Objects.requireNonNull(triggerType, "triggerType must not be null");
        if (sendCycle == null || sendCycle.isBlank()) {
            throw new IllegalArgumentException("sendCycle must not be blank");
        }
        if (triggerType == TriggerType.EVENT && (triggerEventId == null || triggerEventId.isBlank())) {
            throw new IllegalArgumentException("triggerEventId must not be blank when triggerType is EVENT");
        }
    }
}
