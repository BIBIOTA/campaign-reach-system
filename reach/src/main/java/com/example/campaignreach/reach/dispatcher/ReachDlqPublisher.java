package com.example.campaignreach.reach.dispatcher;

import com.example.campaignreach.shared.event.KafkaTopics;
import com.example.campaignreach.shared.event.PartitionKeys;
import com.example.campaignreach.shared.event.ReachTaskDeadLettered;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link ReachTaskDeadLettered} events to the {@code reach.dlq} topic when the dispatcher
 * exhausts a task's bounded retries (task 9.2, spec「失敗保留與 DLQ」, §6 FR-016/US-006). This is the
 * reach module's single producer seam — reach otherwise only consumes Kafka — so topic naming and
 * partition keying for the task DLQ stay in one place.
 *
 * <p>Mirrors {@code campaign}'s {@code ReachRequestPublisher} conventions: a <strong>synchronous,
 * bounded</strong> {@code .get(timeout)} wait, so a broker rejection / serialization failure / timeout
 * all throw and <em>propagate</em> to the dispatcher rather than being swallowed. The dispatcher relies
 * on that to implement "不靜默遺失": it publishes first and only marks the row DLQ if the publish
 * succeeded (publish-then-mark, at-least-once).
 *
 * <p>Messages are keyed via {@link PartitionKeys#forReachDlq(String)} using the originating task's
 * deterministic {@code campaignId:sendCycleKey} composite, so a replayed task preserves its original
 * partition placement (design.md §9).
 *
 * <p>Gated behind {@code campaignreach.kafka.at-least-once.enabled} (the same flag as the reach
 * orchestrator's Kafka wiring) so the producer bean is only created when Kafka is active and never in a
 * no-Kafka unit-test context.
 */
@Component
@ConditionalOnProperty(name = "campaignreach.kafka.at-least-once.enabled", havingValue = "true")
public class ReachDlqPublisher {

    private final KafkaTemplate<String, ReachTaskDeadLettered> kafkaTemplate;
    private final Duration sendTimeout;

    /**
     * @param kafkaTemplate the producer seam to {@code reach.dlq}
     * @param sendTimeout bounded wait for the broker ack ({@code campaignreach.kafka.publish-timeout},
     *     default 10s); caps how long a slow broker can block the dispatcher thread before the publish
     *     fails and propagates (so the row is left unmarked and the task is not lost)
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring injects the singleton KafkaTemplate by reference; storing the "
                    + "framework-managed bean is the intended DI wiring, not a mutable-state leak.")
    public ReachDlqPublisher(
            KafkaTemplate<String, ReachTaskDeadLettered> kafkaTemplate,
            @Value("${campaignreach.kafka.publish-timeout:10s}") Duration sendTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeout = sendTimeout;
    }

    /**
     * Sends one dead-letter event to {@code reach.dlq}, partitioned by the originating task's
     * {@code campaignId:sendCycleKey} composite key, and waits up to {@code sendTimeout} for the broker
     * ack.
     *
     * <p>The wait is synchronous and bounded: any failure (broker rejection, serialization, timeout)
     * throws so the dispatcher does NOT mark the row DLQ — the task stays PROCESSING with an expired
     * lease for the Reaper (task 9.3) to reclaim, guaranteeing it is never silently lost.
     *
     * @param event the dead-letter event (must not be {@code null})
     * @throws IllegalStateException if the send fails or does not complete within {@code sendTimeout}
     */
    public void publish(ReachTaskDeadLettered event) {
        String key = PartitionKeys.forReachDlq(event.campaignId() + ":" + event.sendCycleKey());
        try {
            kafkaTemplate.send(KafkaTopics.REACH_DLQ, key, event).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            throw new IllegalStateException(
                    "Failed to publish ReachTaskDeadLettered for task " + event.reachTaskId(), ex.getCause());
        } catch (TimeoutException ex) {
            throw new IllegalStateException(
                    "Timed out publishing ReachTaskDeadLettered for task " + event.reachTaskId() + " after "
                            + sendTimeout,
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted publishing ReachTaskDeadLettered for task " + event.reachTaskId(), ex);
        }
    }
}
