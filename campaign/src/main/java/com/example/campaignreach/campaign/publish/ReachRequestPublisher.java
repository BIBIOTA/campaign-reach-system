package com.example.campaignreach.campaign.publish;

import com.example.campaignreach.shared.event.KafkaTopics;
import com.example.campaignreach.shared.event.PartitionKeys;
import com.example.campaignreach.shared.event.ReachRequested;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes activity-level {@link ReachRequested} events to the {@code reach.requested} topic (task
 * 6.2, FR-008). This is the single producer seam both campaign trigger paths converge on: the
 * scheduled batch scan (task 6.2) and — later — the behavior-event path (task 6.3) emit the same
 * event type through here, so partition keying and topic naming stay in one place.
 *
 * <p>The campaign module talks to reach <strong>only</strong> through the shared Kafka contract
 * ({@code shared.event}); it never touches reach internals. Messages are keyed via
 * {@link PartitionKeys#forReachRequested(ReachRequested)} — the deterministic
 * {@code campaignId:sendCycle} composite, never bare {@code campaignId} (NFR-002, design.md §9).
 */
@Component
public class ReachRequestPublisher {

    private final KafkaTemplate<String, ReachRequested> kafkaTemplate;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring injects the singleton KafkaTemplate by reference; storing the "
                    + "framework-managed bean is the intended DI wiring, not a mutable-state leak.")
    public ReachRequestPublisher(KafkaTemplate<String, ReachRequested> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends one activity-level reach request to {@code reach.requested}, partitioned by the
     * deterministic {@code campaignId:sendCycle} composite key.
     *
     * @param event the activity-level reach request (carries {@code targetSpec}/{@code reachPlan} but
     *     no recipient list); must not be {@code null}
     */
    public void publish(ReachRequested event) {
        kafkaTemplate.send(KafkaTopics.REACH_REQUESTED, PartitionKeys.forReachRequested(event), event);
    }
}
