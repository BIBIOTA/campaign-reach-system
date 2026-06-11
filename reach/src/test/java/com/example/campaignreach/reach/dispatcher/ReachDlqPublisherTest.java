package com.example.campaignreach.reach.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.campaignreach.shared.event.Channel;
import com.example.campaignreach.shared.event.KafkaTopics;
import com.example.campaignreach.shared.event.ReachTaskDeadLettered;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Fast unit tests for the {@code reach.dlq} publisher's bounded, synchronous publish contract (task
 * 9.2, at-least-once §9). Mirrors {@code ReachRequestPublisherTest}: the KafkaTemplate is mocked so the
 * returned future can be driven to success, failure, and never-completing (timeout) without a broker,
 * and we assert that failures <em>propagate</em> (so the dispatcher does not mark the row dead-lettered
 * and the task is not silently lost) rather than being swallowed.
 */
@ExtendWith(MockitoExtension.class)
class ReachDlqPublisherTest {

    @Mock
    private KafkaTemplate<String, ReachTaskDeadLettered> kafkaTemplate;

    private static ReachTaskDeadLettered event() {
        return new ReachTaskDeadLettered(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Channel.EMAIL,
                "sched:c:2026-06-09T10:00",
                "retries-exhausted:provider timeout",
                3,
                Instant.parse("2026-06-09T10:00:00Z"));
    }

    @Test
    @DisplayName("success: a completed send returns normally and keys by campaignId:sendCycleKey")
    void successfulSendReturnsAndKeysBySourceTaskKey() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResult()));
        ReachDlqPublisher publisher = new ReachDlqPublisher(kafkaTemplate, Duration.ofSeconds(10));
        ReachTaskDeadLettered ev = event();

        assertThatCode(() -> publisher.publish(ev)).doesNotThrowAnyException();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.REACH_DLQ), key.capture(), eq(ev));
        assertThat(key.getValue()).isEqualTo(ev.campaignId() + ":" + ev.sendCycleKey());
    }

    @Test
    @DisplayName("broker rejection: a failed send propagates as IllegalStateException carrying the cause")
    void failedSendPropagates() {
        RuntimeException brokerError = new RuntimeException("broker rejected");
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(brokerError));
        ReachDlqPublisher publisher = new ReachDlqPublisher(kafkaTemplate, Duration.ofSeconds(10));

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(brokerError);
    }

    @Test
    @DisplayName("unresponsive broker: a send that never acks times out and propagates (does not hang)")
    void neverCompletingSendTimesOut() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<>()); // never completes
        ReachDlqPublisher publisher = new ReachDlqPublisher(kafkaTemplate, Duration.ofMillis(50));

        long start = System.nanoTime();
        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timed out");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @SuppressWarnings("unchecked")
    private static SendResult<String, ReachTaskDeadLettered> mockResult() {
        return org.mockito.Mockito.mock(SendResult.class);
    }
}
