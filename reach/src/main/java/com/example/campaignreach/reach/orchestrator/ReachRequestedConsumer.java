package com.example.campaignreach.reach.orchestrator;

import com.example.campaignreach.shared.event.ConsumerGroups;
import com.example.campaignreach.shared.event.KafkaTopics;
import com.example.campaignreach.shared.event.ReachRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Reach-orchestrator entry point (task 7.1; {@code diagrams/01-sequence-reach-flow.puml}). Thin
 * {@code @KafkaListener} adapter over the {@code reach.requested} topic — the single topic both campaign
 * trigger paths converge on — that deserializes a {@link ReachRequested}, hands it to
 * {@link ReachRequestLander} for batch landing + the idempotency/skip decision, then commits the offset.
 *
 * <p><strong>At-least-once (design.md §9).</strong> The listener uses reach's own
 * {@code reachRequestedKafkaListenerContainerFactory} (manual ack, broker auto-commit disabled) and only
 * calls {@link Acknowledgment#acknowledge()} <em>after</em> {@link ReachRequestLander#land} returns —
 * i.e. after the batch has been durably landed. If landing throws (e.g. the DB is unavailable), the
 * offset is not advanced and the event is re-delivered; the dedup key makes that redelivery land no
 * second batch.
 *
 * <p>All batch logic lives in {@link ReachRequestLander} (Kafka-free, unit-testable); this class stays a
 * thin adapter that delegates then acks.
 */
@Component
public class ReachRequestedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ReachRequestedConsumer.class);

    private final ReachRequestLander lander;

    /**
     * @param lander the Kafka-free batch-landing logic
     */
    public ReachRequestedConsumer(ReachRequestLander lander) {
        this.lander = lander;
    }

    /**
     * Consumes one activity-level reach request from {@code reach.requested}, lands the batch, then
     * acknowledges so the offset commits only post-persistence (at-least-once).
     *
     * @param event the deserialized inbound reach request
     * @param acknowledgment manual offset-commit handle from the at-least-once container
     */
    @KafkaListener(
            topics = KafkaTopics.REACH_REQUESTED,
            groupId = ConsumerGroups.REACH_ORCHESTRATOR,
            containerFactory = "reachRequestedKafkaListenerContainerFactory")
    public void onReachRequested(ReachRequested event, Acknowledgment acknowledgment) {
        if (event == null) {
            LOG.warn("Received null reach.requested payload (tombstone). Acknowledging and skipping.");
            acknowledgment.acknowledge();
            return;
        }
        LOG.debug("Consuming reach.requested for campaign {} sendCycle={}", event.campaignId(), event.sendCycle());
        lander.land(event);
        acknowledgment.acknowledge();
    }
}
