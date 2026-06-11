package com.example.campaignreach.campaign.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.campaign.domain.CampaignType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Fast unit tests for the time-driven lifecycle sweep (task 6.1, scenario "起訖時間自動推進"). No Kafka,
 * no Spring context: the {@link CampaignRepository} is stubbed and transitions are asserted against
 * the guarded edges from {@code diagrams/02-state-campaign-and-task-lifecycle.puml}.
 */
@ExtendWith(MockitoExtension.class)
class CampaignLifecycleSchedulerTest {

    private static final Instant PAST = Instant.now().minus(1, ChronoUnit.HOURS);
    private static final Instant FUTURE = Instant.now().plus(1, ChronoUnit.HOURS);

    @Mock
    private CampaignRepository campaignRepository;

    private CampaignLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CampaignLifecycleScheduler(campaignRepository);
    }

    private static Campaign campaign(CampaignStatus status, Instant startAt, Instant endAt) {
        return new Campaign(
                UUID.randomUUID(), "Promo", CampaignType.DISCOUNT, status, startAt, endAt, "{}", "{}", "{}");
    }

    @Test
    @DisplayName("起訖時間自動推進: SCHEDULED with startAt passed → RUNNING (SCHEDULED→RUNNING)")
    void scheduledWithStartPassedAdvancesToRunning() {
        Campaign due = campaign(CampaignStatus.SCHEDULED, PAST, FUTURE);
        when(campaignRepository.findByStatusAndStartAtLessThanEqual(eq(CampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of(due));
        when(campaignRepository.findByStatusInAndEndAtLessThanEqual(ArgumentMatchers.anyCollection(), any()))
                .thenReturn(List.of());

        scheduler.advanceLifecycle();

        assertThat(due.getStatus()).isEqualTo(CampaignStatus.RUNNING);
        verify(campaignRepository).save(due);
    }

    @Test
    @DisplayName("起訖時間自動推進: RUNNING with endAt passed → ENDED (RUNNING→ENDED)")
    void runningWithEndPassedAdvancesToEnded() {
        Campaign due = campaign(CampaignStatus.RUNNING, PAST, PAST);
        when(campaignRepository.findByStatusAndStartAtLessThanEqual(eq(CampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of());
        when(campaignRepository.findByStatusInAndEndAtLessThanEqual(ArgumentMatchers.anyCollection(), any()))
                .thenReturn(List.of(due));

        scheduler.advanceLifecycle();

        assertThat(due.getStatus()).isEqualTo(CampaignStatus.ENDED);
        verify(campaignRepository).save(due);
    }

    @Test
    @DisplayName("起訖時間自動推進: PAUSED with endAt passed → ENDED (PAUSED→ENDED)")
    void pausedWithEndPassedAdvancesToEnded() {
        Campaign due = campaign(CampaignStatus.PAUSED, PAST, PAST);
        when(campaignRepository.findByStatusAndStartAtLessThanEqual(eq(CampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of());
        when(campaignRepository.findByStatusInAndEndAtLessThanEqual(ArgumentMatchers.anyCollection(), any()))
                .thenReturn(List.of(due));

        scheduler.advanceLifecycle();

        assertThat(due.getStatus()).isEqualTo(CampaignStatus.ENDED);
        verify(campaignRepository).save(due);
    }

    @Test
    @DisplayName("起訖時間自動推進: SCHEDULED with both start and end passed converges to ENDED in one tick")
    void scheduledWithStartAndEndPassedConvergesToEnded() {
        Campaign due = campaign(CampaignStatus.SCHEDULED, PAST, PAST);
        // start sweep finds it as SCHEDULED; end sweep is driven by status IN (RUNNING, PAUSED), so the
        // same campaign (now RUNNING after the start sweep) is returned by the end query as well.
        when(campaignRepository.findByStatusAndStartAtLessThanEqual(eq(CampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of(due));
        when(campaignRepository.findByStatusInAndEndAtLessThanEqual(ArgumentMatchers.anyCollection(), any()))
                .thenReturn(List.of(due));

        scheduler.advanceLifecycle();

        assertThat(due.getStatus()).isEqualTo(CampaignStatus.ENDED);
    }

    @Test
    @DisplayName("起訖時間自動推進: not-yet-due SCHEDULED is untouched")
    void notYetDueScheduledUntouched() {
        // The derived query would not return a future-start campaign; the scheduler touches nothing.
        when(campaignRepository.findByStatusAndStartAtLessThanEqual(eq(CampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of());
        when(campaignRepository.findByStatusInAndEndAtLessThanEqual(ArgumentMatchers.anyCollection(), any()))
                .thenReturn(List.of());

        scheduler.advanceLifecycle();

        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("起訖時間自動推進: DRAFT and ENDED campaigns are never auto-advanced")
    void draftAndEndedAreNeverQueried() {
        // DRAFT is excluded by querying status=SCHEDULED for start, and ENDED is excluded by querying
        // status IN (RUNNING, PAUSED) for end — neither status is ever fed to transitionTo.
        when(campaignRepository.findByStatusAndStartAtLessThanEqual(eq(CampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of());
        when(campaignRepository.findByStatusInAndEndAtLessThanEqual(ArgumentMatchers.anyCollection(), any()))
                .thenReturn(List.of());

        scheduler.advanceLifecycle();

        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("per-campaign exception isolation: one failing campaign does not stop the others")
    void perCampaignExceptionIsolation() {
        Campaign bad = campaign(CampaignStatus.SCHEDULED, PAST, FUTURE);
        Campaign good = campaign(CampaignStatus.SCHEDULED, PAST, FUTURE);
        when(campaignRepository.findByStatusAndStartAtLessThanEqual(eq(CampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of(bad, good));
        when(campaignRepository.findByStatusInAndEndAtLessThanEqual(ArgumentMatchers.anyCollection(), any()))
                .thenReturn(List.of());
        when(campaignRepository.save(bad)).thenThrow(new RuntimeException("boom"));

        scheduler.advanceLifecycle();

        // good was still advanced and saved despite bad throwing on save
        assertThat(good.getStatus()).isEqualTo(CampaignStatus.RUNNING);
        verify(campaignRepository).save(good);
    }
}
