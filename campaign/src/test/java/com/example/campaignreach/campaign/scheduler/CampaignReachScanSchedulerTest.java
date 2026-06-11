package com.example.campaignreach.campaign.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.campaign.domain.CampaignType;
import com.example.campaignreach.campaign.evaluation.ReachTriggerEvaluatorRegistry;
import com.example.campaignreach.campaign.evaluation.TriggerContext;
import com.example.campaignreach.campaign.evaluation.TriggerDecision;
import com.example.campaignreach.campaign.publish.ReachRequestPublisher;
import com.example.campaignreach.shared.event.ReachRequested;
import com.example.campaignreach.shared.event.TriggerType;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Fast unit tests for the scheduled reach-scan path (task 6.2, scenarios "排程批次觸發" and "ShedLock 防同一
 * cycle 重複觸發" at the key-derivation level). No Kafka, no Spring context, no DB: the repository,
 * registry, and publisher are all mocked, so these assert the emit/no-emit decision, the
 * activity-level event shape (targetSpec/reachPlan, no recipients), deterministic key derivation, and
 * per-campaign isolation directly.
 */
@ExtendWith(MockitoExtension.class)
class CampaignReachScanSchedulerTest {

    // A cycle boundary (epoch hour) so the campaign window straddles it deterministically.
    private static final Instant PAST = Instant.now().minus(2, ChronoUnit.HOURS);
    private static final Instant FUTURE = Instant.now().plus(2, ChronoUnit.HOURS);

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private ReachTriggerEvaluatorRegistry triggerRegistry;

    @Mock
    private ReachRequestPublisher publisher;

    private CampaignReachScanScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CampaignReachScanScheduler(campaignRepository, triggerRegistry, publisher, Duration.ofHours(1));
    }

    private static Campaign running(Instant startAt, Instant endAt) {
        return new Campaign(
                UUID.randomUUID(),
                "Promo",
                CampaignType.DISCOUNT,
                CampaignStatus.RUNNING,
                startAt,
                endAt,
                "{\"segment\":\"vip\"}",
                "{\"channel\":\"EMAIL\"}",
                "{}");
    }

    @Test
    @DisplayName("排程批次觸發: RUNNING + due + TRIGGER → emits SCHEDULED_BATCH ReachRequested with "
            + "targetSpec/reachPlan and a sched:{id}:{cycleStart} sendCycle, no recipient list")
    void runningDueTriggerEmitsActivityLevelEvent() {
        Campaign campaign = running(PAST, FUTURE);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(campaign));
        when(triggerRegistry.evaluate(any(TriggerContext.class))).thenReturn(TriggerDecision.of(true));

        scheduler.scanAndEmit();

        ArgumentCaptor<ReachRequested> captor = ArgumentCaptor.forClass(ReachRequested.class);
        verify(publisher).publish(captor.capture());
        ReachRequested event = captor.getValue();
        assertThat(event.campaignId()).isEqualTo(campaign.getId());
        assertThat(event.triggerType()).isEqualTo(TriggerType.SCHEDULED_BATCH);
        assertThat(event.triggerEventId()).isNull();
        assertThat(event.targetSpec()).isEqualTo(campaign.getTargetSpec());
        assertThat(event.reachPlan()).isEqualTo(campaign.getReachPlan());
        assertThat(event.sendCycle())
                .startsWith("sched:" + campaign.getId() + ":")
                // activity-level contract carries no recipient list — only the JSON snapshots above.
                .doesNotContain("recipient");
    }

    @Test
    @DisplayName("ScheduledTriggerEvaluator due flag: campaign window straddling the cycle boundary is due")
    void dueComputedFromWindow() {
        Campaign campaign = running(PAST, FUTURE);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(campaign));
        when(triggerRegistry.evaluate(any(TriggerContext.class))).thenReturn(TriggerDecision.of(true));

        scheduler.scanAndEmit();

        ArgumentCaptor<TriggerContext> ctx = ArgumentCaptor.forClass(TriggerContext.class);
        verify(triggerRegistry).evaluate(ctx.capture());
        assertThat(ctx.getValue().due()).isTrue();
    }

    @Test
    @DisplayName("no emission: NO_TRIGGER decision does not publish")
    void noTriggerDoesNotEmit() {
        Campaign campaign = running(PAST, FUTURE);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(campaign));
        when(triggerRegistry.evaluate(any(TriggerContext.class))).thenReturn(TriggerDecision.of(false));

        scheduler.scanAndEmit();

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("no emission: a not-due window yields due=false and (with the real evaluator) no trigger")
    void notDueWindowYieldsNotDue() {
        // Campaign whose active window starts in the future → the floored cycle is before startAt → not due.
        Campaign campaign = running(FUTURE, FUTURE.plus(1, ChronoUnit.HOURS));
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(campaign));
        when(triggerRegistry.evaluate(any(TriggerContext.class))).thenReturn(TriggerDecision.of(false));

        scheduler.scanAndEmit();

        ArgumentCaptor<TriggerContext> ctx = ArgumentCaptor.forClass(TriggerContext.class);
        verify(triggerRegistry).evaluate(ctx.capture());
        assertThat(ctx.getValue().due()).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("no emission: non-RUNNING campaigns are never scanned (query is status=RUNNING only)")
    void nonRunningNeverScanned() {
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of());

        scheduler.scanAndEmit();

        verifyNoInteractions(triggerRegistry, publisher);
    }

    @Test
    @DisplayName("deterministic sendCycle: same campaign + same logical cycle ⇒ identical sendCycle string")
    void deterministicSendCycleKey() {
        UUID campaignId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-06-11T10:05:00Z");
        Instant t2 = Instant.parse("2026-06-11T10:55:00Z"); // same hour cycle as t1

        String key1 = CampaignReachScanScheduler.sendCycleKey(campaignId, scheduler.floorToCycle(t1));
        String key2 = CampaignReachScanScheduler.sendCycleKey(campaignId, scheduler.floorToCycle(t2));

        assertThat(key1).isEqualTo(key2).isEqualTo("sched:" + campaignId + ":2026-06-11T10:00:00Z");
    }

    @Test
    @DisplayName("deterministic sendCycle: a different cycle yields a different key (no now()-instant leak)")
    void differentCycleDifferentKey() {
        UUID campaignId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-06-11T10:05:00Z");
        Instant t2 = Instant.parse("2026-06-11T11:05:00Z"); // next hour cycle

        String key1 = CampaignReachScanScheduler.sendCycleKey(campaignId, scheduler.floorToCycle(t1));
        String key2 = CampaignReachScanScheduler.sendCycleKey(campaignId, scheduler.floorToCycle(t2));

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("per-campaign isolation: one campaign's publish failure does not stop the others")
    void perCampaignIsolation() {
        Campaign bad = running(PAST, FUTURE);
        Campaign good = running(PAST, FUTURE);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(bad, good));
        when(triggerRegistry.evaluate(any(TriggerContext.class))).thenReturn(TriggerDecision.of(true));
        doThrow(new RuntimeException("kafka down"))
                .when(publisher)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        e -> e != null && e.campaignId().equals(bad.getId())));

        scheduler.scanAndEmit();

        // good still emitted despite bad throwing.
        verify(publisher)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        e -> e != null && e.campaignId().equals(good.getId())));
    }

    @Test
    @DisplayName("ShedLock guard present: scanAndEmit is annotated @SchedulerLock with a fixed lock name")
    void scanMethodIsSchedulerLocked() throws NoSuchMethodException {
        Method method = CampaignReachScanScheduler.class.getMethod("scanAndEmit");
        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo(CampaignReachScanScheduler.LOCK_NAME);
    }

    @Test
    @DisplayName("registry isolation reused: a SKIPPED decision (evaluator threw) does not emit")
    void skippedDecisionDoesNotEmit() {
        Campaign campaign = running(PAST, FUTURE);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(campaign));
        when(triggerRegistry.evaluate(any(TriggerContext.class)))
                .thenReturn(TriggerDecision.skipped("evaluator threw"));

        scheduler.scanAndEmit();

        verify(publisher, never()).publish(any());
        // sanity: the mock was consulted exactly once for this single campaign.
        verify(triggerRegistry).evaluate(any(TriggerContext.class));
    }
}
