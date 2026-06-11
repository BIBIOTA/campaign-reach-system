package com.example.campaignreach.campaign.publish;

import com.example.campaignreach.shared.event.KafkaTopics;
import com.example.campaignreach.shared.event.PartitionKeys;
import com.example.campaignreach.shared.event.ReachRequested;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
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
    private final Duration sendTimeout;

    /**
     * @param kafkaTemplate the producer seam to {@code reach.requested}
     * @param sendTimeout bounded wait for the broker ack ({@code campaignreach.kafka.publish-timeout},
     *     default 10s); caps how long a slow/unresponsive broker can block the calling consumer/scan
     *     thread before the send fails and propagates
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring injects the singleton KafkaTemplate by reference; storing the "
                    + "framework-managed bean is the intended DI wiring, not a mutable-state leak.")
    public ReachRequestPublisher(
            KafkaTemplate<String, ReachRequested> kafkaTemplate,
            @Value("${campaignreach.kafka.publish-timeout:10s}") Duration sendTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeout = sendTimeout;
    }

    /**
     * Sends one activity-level reach request to {@code reach.requested}, partitioned by the
     * deterministic {@code campaignId:sendCycle} composite key, and waits up to {@code sendTimeout} for
     * the broker ack.
     *
     * <p>The wait is <strong>synchronous and bounded</strong>: a broker rejection, a serialization
     * failure, or a timeout all throw, so the failure reaches the caller. On the event path this is what
     * lets {@code DomainEventConsumer} skip the ack and have Kafka re-deliver (at-least-once, design.md
     * §9) rather than fire-and-forget swallowing a lost send. The bound prevents an unresponsive broker
     * from blocking the consumer thread indefinitely.
     *
     * @param event the activity-level reach request (carries {@code targetSpec}/{@code reachPlan} but
     *     no recipient list); must not be {@code null}
     * @throws IllegalStateException if the send fails or does not complete within {@code sendTimeout}
     */
    public void publish(ReachRequested event) {
        try {
            kafkaTemplate
                    .send(KafkaTopics.REACH_REQUESTED, PartitionKeys.forReachRequested(event), event)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            throw new IllegalStateException(
                    "Failed to publish ReachRequested for campaign " + event.campaignId(), ex.getCause());
        } catch (TimeoutException ex) {
            throw new IllegalStateException(
                    "Timed out publishing ReachRequested for campaign " + event.campaignId() + " after " + sendTimeout,
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted publishing ReachRequested for campaign " + event.campaignId(), ex);
        }
    }
}
