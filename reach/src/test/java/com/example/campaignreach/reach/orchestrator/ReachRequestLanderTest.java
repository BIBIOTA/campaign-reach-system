package com.example.campaignreach.reach.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.campaignreach.shared.event.ReachRequested;
import com.example.campaignreach.shared.event.TriggerType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Fast unit tests for {@link ReachRequestLander} (task 7.1; spec §5 scenarios "同事件重投只建一筆批次",
 * "已展開完成的批次直接跳過", "凍結快照以利追溯"). No Kafka, no Spring context, no DB: the repository and the
 * audience-expander seam are mocked so the landing / idempotency / skip decision and the frozen
 * snapshots are asserted directly.
 */
@ExtendWith(MockitoExtension.class)
class ReachRequestLanderTest {

    private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SEND_CYCLE = "sched:" + CAMPAIGN_ID + ":2026-06-11T14:00:00Z";
    private static final String TARGET_SPEC = "{\"kind\":\"STATIC_LIST\",\"listId\":\"abc\"}";
    private static final String REACH_PLAN = "{\"channel\":\"EMAIL\",\"templateRef\":\"welcome\"}";

    @Mock
    private ReachRequestRepository repository;

    @Mock
    private AudienceExpander audienceExpander;

    private ReachRequestLander lander;

    @BeforeEach
    void setUp() {
        lander = new ReachRequestLander(repository, audienceExpander);
    }

    private static ReachRequested scheduledEvent() {
        return new ReachRequested(CAMPAIGN_ID, TARGET_SPEC, REACH_PLAN, TriggerType.SCHEDULED_BATCH, SEND_CYCLE, null);
    }

    private ReachRequest existingBatch(ReachRequestStatus status) {
        ReachRequest batch = new ReachRequest(
                UUID.randomUUID(),
                CAMPAIGN_ID,
                TriggerType.SCHEDULED_BATCH,
                null,
                SEND_CYCLE,
                TARGET_SPEC,
                REACH_PLAN,
                java.time.Instant.now());
        batch.setStatus(status);
        return batch;
    }

    @Nested
    @DisplayName("凍結快照以利追溯 + 一筆事件落一筆 PENDING 批次")
    class LandsNewBatch {

        @Test
        @DisplayName("首次消費：插入 PENDING 批次並凍結 target/reach 快照後進入展開")
        void landsPendingBatchWithFrozenSnapshotsThenExpands() {
            when(repository.findByCampaignIdAndSendCycleKeyAndTriggerType(
                            CAMPAIGN_ID, SEND_CYCLE, TriggerType.SCHEDULED_BATCH))
                    .thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(ReachRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            lander.land(scheduledEvent());

            ArgumentCaptor<ReachRequest> saved = ArgumentCaptor.forClass(ReachRequest.class);
            verify(repository).saveAndFlush(saved.capture());
            ReachRequest batch = saved.getValue();
            assertThat(batch.getStatus()).isEqualTo(ReachRequestStatus.PENDING);
            assertThat(batch.getCampaignId()).isEqualTo(CAMPAIGN_ID);
            assertThat(batch.getSendCycleKey()).isEqualTo(SEND_CYCLE);
            assertThat(batch.getTriggerType()).isEqualTo(TriggerType.SCHEDULED_BATCH);
            // Snapshots frozen verbatim from the event (traceable basis).
            assertThat(batch.getTargetSpecSnapshot()).isEqualTo(TARGET_SPEC);
            assertThat(batch.getReachPlanSnapshot()).isEqualTo(REACH_PLAN);
            verify(audienceExpander).expand(batch);
        }
    }

    @Nested
    @DisplayName("同事件重投只建一筆批次")
    class RedeliveryDoesNotCreateSecondBatch {

        @Test
        @DisplayName("已存在 PENDING 批次：不再 insert，續跑展開（resume）")
        void existingPendingResumesExpansionWithoutInsert() {
            ReachRequest existing = existingBatch(ReachRequestStatus.PENDING);
            when(repository.findByCampaignIdAndSendCycleKeyAndTriggerType(
                            CAMPAIGN_ID, SEND_CYCLE, TriggerType.SCHEDULED_BATCH))
                    .thenReturn(Optional.of(existing));

            lander.land(scheduledEvent());

            verify(repository, never()).saveAndFlush(any());
            verify(audienceExpander).expand(existing);
        }

        @Test
        @DisplayName("並發重投輸掉 insert 競態：以 unique 約束擋下，re-read 既有批次而非建第二筆")
        void lostInsertRaceResolvesAgainstExistingBatch() {
            ReachRequest winner = existingBatch(ReachRequestStatus.PENDING);
            when(repository.findByCampaignIdAndSendCycleKeyAndTriggerType(
                            CAMPAIGN_ID, SEND_CYCLE, TriggerType.SCHEDULED_BATCH))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(winner));
            when(repository.saveAndFlush(any(ReachRequest.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_reach_request_dedup"));

            lander.land(scheduledEvent());

            // The losing insert was attempted exactly once; the winner's row is routed (no second batch).
            verify(repository).saveAndFlush(any());
            verify(audienceExpander).expand(winner);
        }
    }

    @Nested
    @DisplayName("已展開完成的批次直接跳過")
    class SkipsCompletedBatch {

        @Test
        @DisplayName("已 DISPATCHING：直接跳過，不重做受眾解析/insert")
        void dispatchingBatchIsSkipped() {
            ReachRequest existing = existingBatch(ReachRequestStatus.DISPATCHING);
            when(repository.findByCampaignIdAndSendCycleKeyAndTriggerType(
                            CAMPAIGN_ID, SEND_CYCLE, TriggerType.SCHEDULED_BATCH))
                    .thenReturn(Optional.of(existing));

            lander.land(scheduledEvent());

            verify(repository, never()).saveAndFlush(any());
            verifyNoInteractions(audienceExpander);
        }

        @Test
        @DisplayName("已 DONE：直接跳過，不重做受眾解析/insert")
        void doneBatchIsSkipped() {
            ReachRequest existing = existingBatch(ReachRequestStatus.DONE);
            when(repository.findByCampaignIdAndSendCycleKeyAndTriggerType(
                            CAMPAIGN_ID, SEND_CYCLE, TriggerType.SCHEDULED_BATCH))
                    .thenReturn(Optional.of(existing));

            lander.land(scheduledEvent());

            verify(repository, never()).saveAndFlush(any());
            verifyNoInteractions(audienceExpander);
        }
    }
}
