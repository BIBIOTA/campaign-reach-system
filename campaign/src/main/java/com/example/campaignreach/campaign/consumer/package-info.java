/**
 * Campaign module — behavior-event consumer seam (path 2).
 *
 * <p>Hosts the {@code domain.events} consumer that turns user behavior events (e.g.
 * {@code CART_ABANDONED}) into activity-level {@code ReachRequested} emissions (task 6.3, FR-008,
 * US-005). {@code DomainEventConsumer} is a thin {@code @KafkaListener} adapter on the shared kernel's
 * gated at-least-once container; {@code BehaviorEventReachTrigger} holds the Kafka-free match/emit
 * logic; {@code DomainBehaviorEvent} is the minimal inbound contract (external → campaign, hence not
 * in the shared kernel).
 *
 * <p>Both trigger paths converge on the shared {@code ReachRequestPublisher} → {@code reach.requested}
 * topic, so the campaign module never touches reach internals — it communicates only through the
 * shared event contract.
 */
package com.example.campaignreach.campaign.consumer;
