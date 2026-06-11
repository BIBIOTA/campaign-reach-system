package com.example.campaignreach.shared.event;

/**
 * Cross-module Kafka topic-name contract (design.md §9). These topic names are the stable wire
 * identifiers campaign and reach communicate through; placing them in the shared kernel keeps a
 * single source of truth and prevents either module from depending on the other.
 *
 * <ul>
 *   <li>{@link #DOMAIN_EVENTS} — behavior events from the e-commerce store (user-level), consumed by
 *       the {@code campaign-trigger} group.
 *   <li>{@link #DOMAIN_EVENTS_DLT} — dead-letter for {@code domain.events}: behavior events the
 *       campaign-trigger consumer could not process (deserialization failure / exhausted publish
 *       retries), so a poison message never blocks its partition. Distinct from {@link #REACH_DLQ},
 *       which is the reach dispatcher's <em>task</em> DLQ.
 *   <li>{@link #REACH_REQUESTED} — activity-level reach requests; both trigger paths (scheduler +
 *       consumer) converge here, consumed solely by the {@code reach-orchestrator} group.
 *   <li>{@link #REACH_DLQ} — reach <em>tasks</em> whose retries are exhausted, for manual / replay tooling.
 *   <li>{@link #SEND_RESULT_RECORDED} — user-level send outcomes emitted by the reach dispatcher.
 * </ul>
 *
 * <p>This is a constants holder: a {@code final} class with a private constructor, not a Spring bean.
 */
public final class KafkaTopics {

    /** Behavior events ({@code CartAbandoned}, {@code OrderPlaced}, …), partitioned by user. */
    public static final String DOMAIN_EVENTS = "domain.events";

    /**
     * Dead-letter topic for {@code domain.events} messages the campaign-trigger consumer cannot
     * process (poison-pill protection, design.md §9). Default Spring {@code <topic>.DLT} convention.
     */
    public static final String DOMAIN_EVENTS_DLT = "domain.events.DLT";

    /** Activity-level reach requests; the single topic both trigger paths converge on. */
    public static final String REACH_REQUESTED = "reach.requested";

    /** Dead-letter topic for reach tasks whose retries are exhausted. */
    public static final String REACH_DLQ = "reach.dlq";

    /** User-level send results emitted after a successful dispatcher provider call. */
    public static final String SEND_RESULT_RECORDED = "send.result.recorded";

    private KafkaTopics() {
        throw new AssertionError("No instances of constants holder KafkaTopics");
    }
}
