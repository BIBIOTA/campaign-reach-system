package com.example.campaignreach.campaign.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.campaignreach.shared.event.ReachRequested;
import com.example.campaignreach.shared.event.TriggerType;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Fast unit tests for the bounded, synchronous publish contract (task 6.2/6.3, at-least-once §9). The
 * KafkaTemplate is mocked so we can drive the returned future to success, failure, and never-completing
 * (timeout) without a broker, and assert that failures <em>propagate</em> (so the event path can decline
 * the ack) rather than being swallowed.
 */
@ExtendWith(MockitoExtension.class)
class ReachRequestPublisherTest {

    @Mock
    private KafkaTemplate<String, ReachRequested> kafkaTemplate;

    private static ReachRequested event() {
        return new ReachRequested(
                UUID.randomUUID(),
                "{\"segment\":\"vip\"}",
                "{\"channel\":\"EMAIL\"}",
                TriggerType.SCHEDULED_BATCH,
                "sched:c:1",
                null);
    }

    @Test
    @DisplayName("success: a completed send returns normally (no throw)")
    void successfulSendReturns() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResult()));
        ReachRequestPublisher publisher = new ReachRequestPublisher(kafkaTemplate, Duration.ofSeconds(10));

        assertThatCode(() -> publisher.publish(event())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("broker rejection: a failed send propagates as IllegalStateException carrying the cause")
    void failedSendPropagates() {
        RuntimeException brokerError = new RuntimeException("broker rejected");
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(brokerError));
        ReachRequestPublisher publisher = new ReachRequestPublisher(kafkaTemplate, Duration.ofSeconds(10));

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(brokerError);
    }

    @Test
    @DisplayName("unresponsive broker: a send that never acks times out and propagates (does not hang)")
    void neverCompletingSendTimesOut() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<>()); // never completes
        ReachRequestPublisher publisher = new ReachRequestPublisher(kafkaTemplate, Duration.ofMillis(50));

        long start = System.nanoTime();
        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timed out");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @SuppressWarnings("unchecked")
    private static SendResult<String, ReachRequested> mockResult() {
        return org.mockito.Mockito.mock(SendResult.class);
    }
}
