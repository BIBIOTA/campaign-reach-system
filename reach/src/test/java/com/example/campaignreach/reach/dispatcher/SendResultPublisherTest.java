package com.example.campaignreach.reach.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.campaignreach.shared.event.KafkaTopics;
import com.example.campaignreach.shared.event.SendResultRecorded;
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

/** Fast unit tests for the dispatcher send-result event publisher. */
@ExtendWith(MockitoExtension.class)
class SendResultPublisherTest {

    @Mock
    private KafkaTemplate<String, SendResultRecorded> kafkaTemplate;

    private static SendResultRecorded event() {
        return new SendResultRecorded(
                UUID.randomUUID(), "provider-msg-1", "SENT", Instant.parse("2026-06-09T10:00:00Z"));
    }

    @Test
    @DisplayName("success: publishes SendResultRecorded keyed by reachTaskId")
    void successfulSendReturnsAndKeysByReachTaskId() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResult()));
        SendResultPublisher publisher = new SendResultPublisher(kafkaTemplate, Duration.ofSeconds(10));
        SendResultRecorded ev = event();

        assertThatCode(() -> publisher.publish(ev)).doesNotThrowAnyException();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.SEND_RESULT_RECORDED), key.capture(), eq(ev));
        assertThat(key.getValue()).isEqualTo(ev.reachTaskId().toString());
    }

    @Test
    @DisplayName("broker rejection: failed send propagates")
    void failedSendPropagates() {
        RuntimeException brokerError = new RuntimeException("broker rejected");
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(brokerError));
        SendResultPublisher publisher = new SendResultPublisher(kafkaTemplate, Duration.ofSeconds(10));

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(brokerError);
    }

    @Test
    @DisplayName("unresponsive broker: send times out and propagates")
    void neverCompletingSendTimesOut() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        SendResultPublisher publisher = new SendResultPublisher(kafkaTemplate, Duration.ofMillis(50));

        long start = System.nanoTime();
        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timed out");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @SuppressWarnings("unchecked")
    private static SendResult<String, SendResultRecorded> mockResult() {
        return org.mockito.Mockito.mock(SendResult.class);
    }
}
