package com.example.campaignreach.reach.orchestrator;

import com.example.campaignreach.shared.event.TriggerType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence port for the {@link ReachRequest} batch (task 7.1).
 *
 * <p>The {@code unique(campaign_id, send_cycle_key, trigger_type)} constraint is the source of truth
 * for batch-landing idempotency (FR-013, NFR-003): the orchestrator looks up by that key before
 * inserting, and the DB constraint is the final guard against a concurrent double-insert race.
 */
public interface ReachRequestRepository extends JpaRepository<ReachRequest, UUID> {

    /**
     * Looks up the single batch matching the dedup key, if one already exists.
     *
     * @param campaignId owning campaign
     * @param sendCycleKey send-cycle key (event's sendCycle value)
     * @param triggerType trigger source
     * @return the existing batch, or empty when this is the first delivery for the key
     */
    Optional<ReachRequest> findByCampaignIdAndSendCycleKeyAndTriggerType(
            UUID campaignId, String sendCycleKey, TriggerType triggerType);
}
