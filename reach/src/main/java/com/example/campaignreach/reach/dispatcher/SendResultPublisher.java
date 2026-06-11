package com.example.campaignreach.reach.dispatcher;

import com.example.campaignreach.shared.event.KafkaTopics;
import com.example.campaignreach.shared.event.PartitionKeys;
import com.example.campaignreach.shared.event.SendResultRecorded;
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
 * Publishes user-level {@link SendResultRecorded} events after the dispatcher records a successful send.
 */
@Component
@ConditionalOnProperty(name = "campaignreach.kafka.at-least-once.enabled", havingValue = "true")
public class SendResultPublisher {

    private final KafkaTemplate<String, SendResultRecorded> kafkaTemplate;
    private final Duration sendTimeout;

    /**
     * @param kafkaTemplate the producer seam to {@code send.result.recorded}
     * @param sendTimeout bounded wait for the broker ack ({@code campaignreach.kafka.publish-timeout},
     *     default 10s)
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring injects the singleton KafkaTemplate by reference; storing the "
                    + "framework-managed bean is the intended DI wiring, not a mutable-state leak.")
    public SendResultPublisher(
            KafkaTemplate<String, SendResultRecorded> kafkaTemplate,
            @Value("${campaignreach.kafka.publish-timeout:10s}") Duration sendTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeout = sendTimeout;
    }

    /**
     * Sends one send-result event, partitioned by {@code reachTaskId}, and waits up to
     * {@code sendTimeout} for the broker ack.
     *
     * @param event the send-result event (must not be {@code null})
     * @throws IllegalStateException if the send fails or does not complete within {@code sendTimeout}
     */
    public void publish(SendResultRecorded event) {
        try {
            kafkaTemplate
                    .send(
                            KafkaTopics.SEND_RESULT_RECORDED,
                            PartitionKeys.forSendResultRecorded(event.reachTaskId()),
                            event)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            throw new IllegalStateException(
                    "Failed to publish SendResultRecorded for task " + event.reachTaskId(), ex.getCause());
        } catch (TimeoutException ex) {
            throw new IllegalStateException(
                    "Timed out publishing SendResultRecorded for task " + event.reachTaskId() + " after " + sendTimeout,
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted publishing SendResultRecorded for task " + event.reachTaskId(), ex);
        }
    }
}
