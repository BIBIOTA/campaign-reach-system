package com.example.campaignreach.reach.orchestrator;

import com.example.campaignreach.shared.event.ReachRequested;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka-free batch-landing logic for an inbound {@link ReachRequested} (task 7.1, spec §5, FR-013,
 * NFR-003). It lands exactly one activity-level {@link ReachRequest} per campaign trigger, deduplicated
 * by the {@code unique(campaign_id, send_cycle_key, trigger_type)} key, freezes the
 * {@code target_spec}/{@code reach_plan} snapshots, and decides whether to (re-)enter audience expansion
 * or skip an already-completed batch.
 *
 * <p><strong>Idempotency (NFR-003).</strong> On consume it first looks up the existing batch by the
 * dedup key:
 *
 * <ul>
 *   <li><em>Absent</em> — insert a {@link ReachRequestStatus#PENDING} batch with frozen snapshots, then
 *       hand it to the {@link AudienceExpander}. A concurrent double-insert (same key, two deliveries)
 *       is caught via the DB unique constraint ({@link DataIntegrityViolationException}) and resolved by
 *       re-reading the row the other delivery landed — so redelivery never creates a second batch nor
 *       re-pollutes the counts.
 *   <li><em>Present and {@link ReachRequestStatus#DISPATCHING} / {@link ReachRequestStatus#DONE}</em> —
 *       fan-out has already completed and {@code total_count} is backfilled, so expansion is skipped
 *       entirely (the caller acks); redelivery does not re-resolve the audience or re-insert tasks.
 *   <li><em>Present and {@link ReachRequestStatus#PENDING} / {@link ReachRequestStatus#EXPANDING}</em> —
 *       a partially-expanded batch is resumed by re-entering expansion (the expander is itself
 *       crash-resumable in task 7.3).
 * </ul>
 *
 * <p>This class is deliberately Kafka-free so it can be unit-tested without a broker; the
 * {@link ReachRequestedConsumer} is the thin {@code @KafkaListener} adapter that calls it then acks
 * (at-least-once, design.md §9).
 */
@Component
public class ReachRequestLander {

    private static final Logger LOG = LoggerFactory.getLogger(ReachRequestLander.class);

    private final ReachRequestRepository repository;
    private final AudienceExpander audienceExpander;

    /**
     * @param repository persistence port whose unique constraint is the dedup source of truth
     * @param audienceExpander seam that resolves {@code targetSpec} and fans the batch out into tasks
     *     (real implementation arrives in task 7.3; a no-op default keeps the landing path wired)
     */
    public ReachRequestLander(ReachRequestRepository repository, AudienceExpander audienceExpander) {
        this.repository = repository;
        this.audienceExpander = audienceExpander;
    }

    /**
     * Lands the batch for one {@link ReachRequested} and routes it to expansion or skip per the
     * idempotency rules above. Safe to call again on Kafka redelivery: it never creates a second batch.
     *
     * @param event the activity-level reach request (carries {@code targetSpec}/{@code reachPlan} JSON
     *     snapshots but no recipient list); must not be {@code null}
     */
    @Transactional
    public void land(ReachRequested event) {
        Optional<ReachRequest> existing = repository.findByCampaignIdAndSendCycleKeyAndTriggerType(
                event.campaignId(), event.sendCycle(), event.triggerType());
        if (existing.isPresent()) {
            routeExisting(existing.get());
            return;
        }

        ReachRequest landed;
        try {
            landed = repository.saveAndFlush(newBatch(event));
        } catch (DataIntegrityViolationException race) {
            // Concurrent delivery of the same (campaign_id, send_cycle_key, trigger_type) won the insert;
            // the unique constraint rejected ours. Re-read and route the row the other delivery landed so
            // we never create a second batch.
            LOG.debug(
                    "Lost insert race for campaign {} sendCycle={}; resolving against existing batch",
                    event.campaignId(),
                    event.sendCycle());
            ReachRequest winner = repository
                    .findByCampaignIdAndSendCycleKeyAndTriggerType(
                            event.campaignId(), event.sendCycle(), event.triggerType())
                    .orElseThrow(() -> new IllegalStateException(
                            "reach_request unique-constraint violation but no existing batch found for campaign "
                                    + event.campaignId() + " sendCycle=" + event.sendCycle(),
                            race));
            routeExisting(winner);
            return;
        }
        LOG.info(
                "Landed reach_request {} for campaign {} sendCycle={} triggerType={}",
                landed.getId(),
                landed.getCampaignId(),
                landed.getSendCycleKey(),
                landed.getTriggerType());
        audienceExpander.expand(landed);
    }

    /**
     * Routes an already-existing batch: an already-dispatched/done batch is skipped (fan-out complete),
     * otherwise expansion is (re-)entered to resume a partial fan-out.
     */
    private void routeExisting(ReachRequest existing) {
        if (existing.getStatus() == ReachRequestStatus.DISPATCHING || existing.getStatus() == ReachRequestStatus.DONE) {
            LOG.debug(
                    "Skipping reach_request {} for campaign {}: already {} (fan-out complete)",
                    existing.getId(),
                    existing.getCampaignId(),
                    existing.getStatus());
            return;
        }
        LOG.debug(
                "Resuming expansion for reach_request {} (campaign {}) in status {}",
                existing.getId(),
                existing.getCampaignId(),
                existing.getStatus());
        audienceExpander.expand(existing);
    }

    /** Builds a freshly landed PENDING batch with the event's snapshots frozen verbatim. */
    private ReachRequest newBatch(ReachRequested event) {
        return new ReachRequest(
                UUID.randomUUID(),
                event.campaignId(),
                event.triggerType(),
                event.triggerEventId(),
                event.sendCycle(),
                event.targetSpec(),
                event.reachPlan(),
                Instant.now());
    }
}
