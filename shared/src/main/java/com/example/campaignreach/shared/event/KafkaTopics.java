package com.example.campaignreach.shared.event;

/**
 * Cross-module Kafka topic-name contract (design.md §9). These three topic names are the stable
 * wire identifiers campaign and reach communicate through; placing them in the shared kernel keeps a
 * single source of truth and prevents either module from depending on the other.
 *
 * <ul>
 *   <li>{@link #DOMAIN_EVENTS} — behavior events from the e-commerce store (user-level), consumed by
 *       the {@code campaign-trigger} group.
 *   <li>{@link #REACH_REQUESTED} — activity-level reach requests; both trigger paths (scheduler +
 *       consumer) converge here, consumed solely by the {@code reach-orchestrator} group.
 *   <li>{@link #REACH_DLQ} — tasks whose retries are exhausted, for manual / replay tooling.
 * </ul>
 *
 * <p>This is a constants holder: a {@code final} class with a private constructor, not a Spring bean.
 */
public final class KafkaTopics {

    /** Behavior events ({@code CartAbandoned}, {@code OrderPlaced}, …), partitioned by user. */
    public static final String DOMAIN_EVENTS = "domain.events";

    /** Activity-level reach requests; the single topic both trigger paths converge on. */
    public static final String REACH_REQUESTED = "reach.requested";

    /** Dead-letter topic for tasks whose retries are exhausted. */
    public static final String REACH_DLQ = "reach.dlq";

    private KafkaTopics() {
        throw new AssertionError("No instances of constants holder KafkaTopics");
    }
}
