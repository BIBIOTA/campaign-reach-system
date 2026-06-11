package com.example.campaignreach.reach.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.campaignreach.reach.channel.ChannelAdapter;
import com.example.campaignreach.reach.channel.NonRetryableSendException;
import com.example.campaignreach.reach.channel.ReachMessage;
import com.example.campaignreach.reach.channel.RetryableSendException;
import com.example.campaignreach.reach.channel.SendResult;
import com.example.campaignreach.reach.channel.SuppressionGuard;
import com.example.campaignreach.reach.channel.SuppressionReason;
import com.example.campaignreach.reach.channel.SuppressionVerdict;
import com.example.campaignreach.shared.event.Channel;
import com.example.campaignreach.shared.event.ReachTaskDeadLettered;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Behaviour-driven tests for {@link ReachTaskDispatcher} (task 9.1). The DAO and channel adapter are
 * mocked to drive the two-phase claim → send → write-back path; assertions map to the spec scenarios
 * (PROCESSING claim, retryable backoff, non-retryable FAILED, breaker pre-skip, breaker fast-fail
 * after PROCESSING). The backoff schedule and max-attempts bound are exercised here end-to-end and in
 * isolation in {@link RetryBackoffScheduleTest}.
 */
@ExtendWith(MockitoExtension.class)
class ReachTaskDispatcherTest {

    @Mock
    private ReachTaskDispatchDao dispatchDao;

    @Mock
    private SuppressionGuard suppressionGuard;

    @Mock
    private ChannelAdapter emailAdapter;

    @Mock
    private ReachDlqPublisher dlqPublisher;

    @Mock
    private ObjectProvider<ReachDlqPublisher> dlqPublisherProvider;

    private ReachTaskDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(emailAdapter.channel()).thenReturn(Channel.EMAIL);
        // Default: recipients are not suppressed unless a test overrides it (the suppression-hit case).
        lenient().when(suppressionGuard.evaluate(any(), any())).thenReturn(SuppressionVerdict.notSuppressed());
        // Default: a DLQ publisher is available (the exhaustion-path tests rely on it).
        lenient().when(dlqPublisherProvider.getIfAvailable()).thenReturn(dlqPublisher);
        ChannelAdapterRegistry registry = new ChannelAdapterRegistry(List.of(emailAdapter));
        dispatcher = new ReachTaskDispatcher(
                dispatchDao,
                registry,
                suppressionGuard,
                dlqPublisherProvider,
                new DispatcherProperties(50, Duration.ofMinutes(5)));
    }

    private ClaimedTask task(int retryCount) {
        return new ClaimedTask(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Channel.EMAIL,
                "cycle-key",
                "welcome",
                retryCount);
    }

    private void claimReturns(ClaimedTask... tasks) {
        when(emailAdapter.isAvailable()).thenReturn(true);
        when(dispatchDao.claimBatch(anyString(), anyList(), anyInt(), any(), any()))
                .thenReturn(List.of(tasks));
    }

    @Nested
    @DisplayName("撈取並標記 PROCESSING（短事務）")
    class ClaimAndMarkProcessing {

        @Test
        @DisplayName("成功送出 → 階段二回寫 SENT 並寫 send_result（含 provider message id）")
        void successWritesBackSent() {
            ClaimedTask t = task(0);
            claimReturns(t);
            when(emailAdapter.send(any(ReachMessage.class))).thenReturn(new SendResult("prov-123"));

            dispatcher.dispatchPoll();

            ArgumentCaptor<ReachMessage> msg = ArgumentCaptor.forClass(ReachMessage.class);
            verify(emailAdapter).send(msg.capture());
            assertThat(msg.getValue().userId()).isEqualTo(t.userId());
            assertThat(msg.getValue().channel()).isEqualTo(Channel.EMAIL);
            assertThat(msg.getValue().templateRef()).isEqualTo("welcome");
            verify(dispatchDao).markSent(eq(t.taskId()), eq(dispatcher.workerId()), eq("prov-123"), any());
            verify(dispatchDao, never()).scheduleRetry(any(), any(), any(), any(), any());
            verify(dispatchDao, never()).markFailed(any(), any(), any(), any());
        }

        @Test
        @DisplayName("claimBatch 僅針對可用通道撈取（傳入 eligibleChannels）")
        void claimsOnlyForAvailableChannels() {
            when(emailAdapter.isAvailable()).thenReturn(true);
            when(dispatchDao.claimBatch(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(List.of());

            dispatcher.dispatchPoll();

            ArgumentCaptor<List<Channel>> channels = ArgumentCaptor.forClass(List.class);
            verify(dispatchDao).claimBatch(anyString(), channels.capture(), anyInt(), any(), any());
            assertThat(channels.getValue()).containsExactly(Channel.EMAIL);
        }
    }

    @Nested
    @DisplayName("可重試失敗走指數退避")
    class RetryableFailureBacksOff {

        @Test
        @DisplayName("可重試錯誤（provider failure）→ RETRY_SCHEDULED，首次退避 1m")
        void retryableProviderFailureSchedulesFirstBackoff() {
            ClaimedTask t = task(0);
            claimReturns(t);
            when(emailAdapter.send(any(ReachMessage.class)))
                    .thenThrow(RetryableSendException.providerFailure("503", new RuntimeException()));

            Instant before = Instant.now();
            dispatcher.dispatchPoll();

            ArgumentCaptor<Instant> nextRetry = ArgumentCaptor.forClass(Instant.class);
            verify(dispatchDao)
                    .scheduleRetry(eq(t.taskId()), eq(dispatcher.workerId()), nextRetry.capture(), anyString(), any());
            // First retry uses the 1m delay.
            assertThat(nextRetry.getValue()).isAfterOrEqualTo(before.plus(Duration.ofMinutes(1)));
            verify(dispatchDao, never()).markSent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("第二次失敗退避 5m、第三次退避 30m（指數退避序列）")
        void secondAndThirdRetryUseFiveAndThirtyMinutes() {
            ClaimedTask second = task(1);
            ClaimedTask third = task(2);
            when(emailAdapter.isAvailable()).thenReturn(true);
            when(dispatchDao.claimBatch(anyString(), anyList(), anyInt(), any(), any()))
                    .thenReturn(List.of(second, third));
            when(emailAdapter.send(any(ReachMessage.class)))
                    .thenThrow(RetryableSendException.providerFailure("503", new RuntimeException()));

            Instant before = Instant.now();
            dispatcher.dispatchPoll();

            ArgumentCaptor<Instant> nextRetry = ArgumentCaptor.forClass(Instant.class);
            verify(dispatchDao, times(2)).scheduleRetry(any(), anyString(), nextRetry.capture(), anyString(), any());
            assertThat(nextRetry.getAllValues().get(0)).isAfterOrEqualTo(before.plus(Duration.ofMinutes(5)));
            assertThat(nextRetry.getAllValues().get(1)).isAfterOrEqualTo(before.plus(Duration.ofMinutes(30)));
        }

        @Test
        @DisplayName("重試耗盡（已重試 3 次後再失敗）→ 進 reach.dlq 並標記 DLQ，不再排程重試")
        void exhaustedRetriesAreDeadLettered() {
            ClaimedTask exhausted = task(RetryBackoffSchedule.MAX_ATTEMPTS);
            claimReturns(exhausted);
            when(emailAdapter.send(any(ReachMessage.class)))
                    .thenThrow(RetryableSendException.providerFailure("503", new RuntimeException()));

            dispatcher.dispatchPoll();

            verify(dlqPublisher).publish(any(ReachTaskDeadLettered.class));
            verify(dispatchDao).markDeadLettered(eq(exhausted.taskId()), eq(dispatcher.workerId()), anyString(), any());
            verify(dispatchDao, never()).scheduleRetry(any(), any(), any(), any(), any());
            verify(dispatchDao, never()).markFailed(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("重試耗盡進 DLQ（不靜默遺失）")
    class ExhaustionDeadLetters {

        @Test
        @DisplayName("耗盡 → 先 publish reach.dlq 再標記 DLQ（publish 在 mark 之前）")
        void publishesBeforeMarking() {
            ClaimedTask exhausted = task(RetryBackoffSchedule.MAX_ATTEMPTS);
            claimReturns(exhausted);
            when(emailAdapter.send(any(ReachMessage.class)))
                    .thenThrow(RetryableSendException.providerFailure("503", new RuntimeException()));

            dispatcher.dispatchPoll();

            InOrder order = inOrder(dlqPublisher, dispatchDao);
            order.verify(dlqPublisher).publish(any(ReachTaskDeadLettered.class));
            order.verify(dispatchDao)
                    .markDeadLettered(eq(exhausted.taskId()), eq(dispatcher.workerId()), anyString(), any());
        }

        @Test
        @DisplayName("dead-letter 事件帶足供人工檢視/重放的識別欄位（無 PII）")
        void deadLetterEventCarriesIdentificationFields() {
            ClaimedTask exhausted = task(RetryBackoffSchedule.MAX_ATTEMPTS);
            claimReturns(exhausted);
            when(emailAdapter.send(any(ReachMessage.class)))
                    .thenThrow(RetryableSendException.providerFailure("503", new RuntimeException()));

            dispatcher.dispatchPoll();

            ArgumentCaptor<ReachTaskDeadLettered> event = ArgumentCaptor.forClass(ReachTaskDeadLettered.class);
            verify(dlqPublisher).publish(event.capture());
            assertThat(event.getValue().reachTaskId()).isEqualTo(exhausted.taskId());
            assertThat(event.getValue().campaignId()).isEqualTo(exhausted.campaignId());
            assertThat(event.getValue().userId()).isEqualTo(exhausted.userId());
            assertThat(event.getValue().channel()).isEqualTo(Channel.EMAIL);
            assertThat(event.getValue().attempts()).isEqualTo(RetryBackoffSchedule.MAX_ATTEMPTS);
        }

        @Test
        @DisplayName("publish 失敗 → 不標記 DLQ（任務不靜默遺失，留待 Reaper 重新取走）")
        void publishFailureLeavesRowUnmarked() {
            ClaimedTask exhausted = task(RetryBackoffSchedule.MAX_ATTEMPTS);
            claimReturns(exhausted);
            when(emailAdapter.send(any(ReachMessage.class)))
                    .thenThrow(RetryableSendException.providerFailure("503", new RuntimeException()));
            // The publish throws (broker down / timeout): the row must NOT be marked, so it is not lost.
            org.mockito.Mockito.doThrow(new IllegalStateException("broker down"))
                    .when(dlqPublisher)
                    .publish(any(ReachTaskDeadLettered.class));

            // The per-task broad catch isolates the failure; the poll completes without throwing.
            dispatcher.dispatchPoll();

            verify(dlqPublisher).publish(any(ReachTaskDeadLettered.class));
            verify(dispatchDao, never()).markDeadLettered(any(), any(), any(), any());
            verify(dispatchDao, never()).markFailed(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("不可重試直接失敗")
    class NonRetryableFailure {

        @Test
        @DisplayName("抑制名單命中（退訂）→ FAILED 且不送出")
        void suppressedRecipientFailsWithoutSending() {
            ClaimedTask t = task(0);
            claimReturns(t);
            when(suppressionGuard.evaluate(t.userId(), Channel.EMAIL))
                    .thenReturn(SuppressionVerdict.suppressed(SuppressionReason.UNSUBSCRIBE));

            dispatcher.dispatchPoll();

            verify(emailAdapter, never()).send(any());
            verify(dispatchDao).markFailed(eq(t.taskId()), eq(dispatcher.workerId()), anyString(), any());
            verify(dispatchDao, never()).scheduleRetry(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("外部呼叫回傳不可重試原因（無效地址）→ 立即 FAILED，不排程重試、不增加重試次數")
        void nonRetryableProviderFailureFailsImmediately() {
            ClaimedTask t = task(0);
            claimReturns(t);
            when(emailAdapter.send(any(ReachMessage.class)))
                    .thenThrow(new NonRetryableSendException("invalid recipient address"));

            dispatcher.dispatchPoll();

            verify(dispatchDao).markFailed(eq(t.taskId()), eq(dispatcher.workerId()), anyString(), any());
            // No retry burned and no dead-lettering: a permanent failure terminates straight to FAILED.
            verify(dispatchDao, never()).scheduleRetry(any(), any(), any(), any(), any());
            verify(dispatchDao, never()).markDeadLettered(any(), any(), any(), any());
            verifyNoInteractions(dlqPublisher);
        }
    }

    @Nested
    @DisplayName("breaker 開啟時不卡任務")
    class BreakerOpenDoesNotBlock {

        @Test
        @DisplayName("breaker 於標 PROCESSING 前已開啟 → 不撈該通道、任務維持 PENDING")
        void openBreakerSkipsClaimLeavingTasksPending() {
            when(emailAdapter.isAvailable()).thenReturn(false);

            dispatcher.dispatchPoll();

            // No eligible channel → never claim, never send, never write back: tasks stay PENDING.
            verifyNoInteractions(dispatchDao);
            verify(emailAdapter, never()).send(any());
        }
    }

    @Nested
    @DisplayName("已 PROCESSING 後 breaker 失敗")
    class BreakerFailsAfterProcessing {

        @Test
        @DisplayName("breaker 在已標 PROCESSING 後快速失敗 → 比照可重試走 RETRY_SCHEDULED，不卡在 PROCESSING")
        void breakerFastFailAfterClaimSchedulesRetry() {
            ClaimedTask t = task(0);
            claimReturns(t);
            // Breaker was available at pre-check (task claimed), then short-circuits the send.
            when(emailAdapter.send(any(ReachMessage.class)))
                    .thenThrow(RetryableSendException.breakerOpen("open", new RuntimeException()));

            dispatcher.dispatchPoll();

            verify(dispatchDao).scheduleRetry(eq(t.taskId()), eq(dispatcher.workerId()), any(), anyString(), any());
            verify(dispatchDao, never()).markSent(any(), any(), any(), any());
        }
    }
}
