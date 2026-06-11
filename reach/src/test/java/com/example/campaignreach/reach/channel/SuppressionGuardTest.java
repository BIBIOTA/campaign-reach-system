package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.campaignreach.reach.orchestrator.ReachTaskStatus;
import com.example.campaignreach.shared.event.Channel;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Fast unit tests for {@link SuppressionGuard} — the pre-send suppression-list filter (task 8.2; spec
 * requirement「收件人 PII 最小化與抑制名單」, Scenario「發送前抑制名單過濾 (FR-015)」; design.md §10).
 *
 * <p>The repository is mocked to control whether a recipient has a suppression row; we drive the guard
 * and assert on the returned {@link SuppressionVerdict} — that a hit yields「suppressed → FAILED, do
 * not send」carrying the reason, that a miss yields「proceed」, and that the lookup is channel-scoped.
 */
@ExtendWith(MockitoExtension.class)
class SuppressionGuardTest {

    @Mock
    private SuppressionRepository suppressionRepository;

    @InjectMocks
    private SuppressionGuard guard;

    private static SuppressionEntry entryWithReason(SuppressionReason reason) {
        SuppressionEntry mock = org.mockito.Mockito.mock(SuppressionEntry.class);
        when(mock.getReason()).thenReturn(reason);
        return mock;
    }

    @Nested
    @DisplayName("發送前抑制名單過濾 (FR-015)")
    class PreSendSuppressionFilter {

        @Test
        @DisplayName("命中退訂 → 標 FAILED（不可重試）且不送出，verdict 帶 UNSUBSCRIBE 原因")
        void hitOnUnsubscribeYieldsFailedDoNotSendWithReason() {
            UUID userId = UUID.randomUUID();
            SuppressionEntry hit = entryWithReason(SuppressionReason.UNSUBSCRIBE);
            when(suppressionRepository.findByUserIdAndChannel(userId, Channel.EMAIL))
                    .thenReturn(Optional.of(hit));

            SuppressionVerdict verdict = guard.evaluate(userId, Channel.EMAIL);

            assertThat(verdict.suppressed()).isTrue();
            assertThat(verdict.reason()).isEqualTo(SuppressionReason.UNSUBSCRIBE);
            assertThat(verdict.failedStatus()).isEqualTo(ReachTaskStatus.FAILED);
        }

        @Test
        @DisplayName("命中硬退信 → suppressed 且原因為 HARD_BOUNCE")
        void hitOnHardBounceYieldsSuppressedWithReason() {
            UUID userId = UUID.randomUUID();
            SuppressionEntry hit = entryWithReason(SuppressionReason.HARD_BOUNCE);
            when(suppressionRepository.findByUserIdAndChannel(userId, Channel.EMAIL))
                    .thenReturn(Optional.of(hit));

            SuppressionVerdict verdict = guard.evaluate(userId, Channel.EMAIL);

            assertThat(verdict.suppressed()).isTrue();
            assertThat(verdict.reason()).isEqualTo(SuppressionReason.HARD_BOUNCE);
        }

        @Test
        @DisplayName("命中投訴 → suppressed 且原因為 COMPLAINT")
        void hitOnComplaintYieldsSuppressedWithReason() {
            UUID userId = UUID.randomUUID();
            SuppressionEntry hit = entryWithReason(SuppressionReason.COMPLAINT);
            when(suppressionRepository.findByUserIdAndChannel(userId, Channel.PUSH))
                    .thenReturn(Optional.of(hit));

            SuppressionVerdict verdict = guard.evaluate(userId, Channel.PUSH);

            assertThat(verdict.suppressed()).isTrue();
            assertThat(verdict.reason()).isEqualTo(SuppressionReason.COMPLAINT);
        }

        @Test
        @DisplayName("未命中 → not suppressed，准予發送")
        void missYieldsNotSuppressedProceed() {
            UUID userId = UUID.randomUUID();
            when(suppressionRepository.findByUserIdAndChannel(userId, Channel.EMAIL))
                    .thenReturn(Optional.empty());

            SuppressionVerdict verdict = guard.evaluate(userId, Channel.EMAIL);

            assertThat(verdict.suppressed()).isFalse();
            assertThat(verdict.reason()).isNull();
        }
    }

    @Nested
    @DisplayName("抑制名單為通道限定 (channel-scoped)")
    class SuppressionIsChannelScoped {

        @Test
        @DisplayName("SMS 退訂不影響同一使用者的 EMAIL 發送")
        void smsSuppressionDoesNotSuppressEmailForSameUser() {
            UUID userId = UUID.randomUUID();
            // Suppressed on SMS only; the EMAIL lookup misses.
            when(suppressionRepository.findByUserIdAndChannel(userId, Channel.EMAIL))
                    .thenReturn(Optional.empty());

            SuppressionVerdict verdict = guard.evaluate(userId, Channel.EMAIL);

            assertThat(verdict.suppressed()).isFalse();
        }

        @Test
        @DisplayName("EMAIL 退訂不影響同一使用者的 SMS 發送")
        void emailSuppressionDoesNotSuppressSmsForSameUser() {
            UUID userId = UUID.randomUUID();
            // Suppressed on EMAIL only; the SMS lookup misses.
            when(suppressionRepository.findByUserIdAndChannel(userId, Channel.SMS))
                    .thenReturn(Optional.empty());

            SuppressionVerdict verdict = guard.evaluate(userId, Channel.SMS);

            assertThat(verdict.suppressed()).isFalse();
        }
    }
}
