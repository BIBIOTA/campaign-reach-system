package com.example.campaignreach.shared.event;

/**
 * Cross-module Kafka consumer-group id contract (design.md §9). Stable group ids keep offset
 * tracking consistent across restarts and across both modules; living in the shared kernel keeps a
 * single source of truth without campaign ↔ reach coupling.
 *
 * <ul>
 *   <li>{@link #CAMPAIGN_TRIGGER} — campaign module consuming {@link KafkaTopics#DOMAIN_EVENTS}.
 *   <li>{@link #REACH_ORCHESTRATOR} — reach orchestrator, the sole consumer of {@link
 *       KafkaTopics#REACH_REQUESTED}.
 * </ul>
 *
 * <p>This is a constants holder: a {@code final} class with a private constructor, not a Spring bean.
 */
public final class ConsumerGroups {

    /** Campaign-side group consuming behavior events from {@link KafkaTopics#DOMAIN_EVENTS}. */
    public static final String CAMPAIGN_TRIGGER = "campaign-trigger";

    /** Reach-side group consuming activity-level requests from {@link KafkaTopics#REACH_REQUESTED}. */
    public static final String REACH_ORCHESTRATOR = "reach-orchestrator";

    private ConsumerGroups() {
        throw new AssertionError("No instances of constants holder ConsumerGroups");
    }
}
