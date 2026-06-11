package com.example.campaignreach.campaign.consumer;

import com.example.campaignreach.shared.event.ConsumerGroups;
import com.example.campaignreach.shared.event.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Path 2 entry point (task 6.3, FR-008, US-005; {@code diagrams/01-sequence-reach-flow.puml}). Thin
 * {@code @KafkaListener} adapter over the {@code domain.events} topic: it deserializes a behavior
 * event, hands it to {@link BehaviorEventReachTrigger} for matching + emission, then commits the
 * offset.
 *
 * <p><strong>At-least-once (design.md §9).</strong> The listener uses the shared kernel's gated
 * {@code atLeastOnceKafkaListenerContainerFactory} (manual ack, broker auto-commit disabled) and only
 * calls {@link Acknowledgment#acknowledge()} <em>after</em> handling returns — i.e. after every matched
 * campaign's {@link com.example.campaignreach.shared.event.ReachRequested} has been handed off to the
 * publisher. If processing throws before the ack, the offset is not advanced and the event is
 * re-delivered. Cross-event idempotency is a reach concern (frequency-capping), out of scope here.
 *
 * <p>All trigger logic lives in {@link BehaviorEventReachTrigger} (Kafka-free, unit-testable); this
 * class stays a thin adapter that delegates then acks.
 */
@Component
public class DomainEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DomainEventConsumer.class);

    private final BehaviorEventReachTrigger reachTrigger;

    /**
     * @param reachTrigger the Kafka-free handler that matches the event and emits {@code ReachRequested}
     */
    public DomainEventConsumer(BehaviorEventReachTrigger reachTrigger) {
        this.reachTrigger = reachTrigger;
    }

    /**
     * Consumes one behavior event from {@code domain.events}, runs the trigger match/emit, then
     * acknowledges so the offset commits only post-processing (at-least-once).
     *
     * @param event the deserialized inbound behavior event
     * @param acknowledgment manual offset-commit handle from the at-least-once container
     */
    @KafkaListener(
            topics = KafkaTopics.DOMAIN_EVENTS,
            groupId = ConsumerGroups.CAMPAIGN_TRIGGER,
            containerFactory = "atLeastOnceKafkaListenerContainerFactory")
    public void onDomainEvent(DomainBehaviorEvent event, Acknowledgment acknowledgment) {
        if (event == null) {
            LOG.warn("Received null domain event payload (tombstone). Acknowledging and skipping.");
            acknowledgment.acknowledge();
            return;
        }
        LOG.debug("Consuming domain event {} type={}", event.eventId(), event.eventType());
        reachTrigger.handle(event);
        acknowledgment.acknowledge();
    }
}
