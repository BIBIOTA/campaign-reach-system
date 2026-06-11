package com.example.campaignreach.campaign.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * Fast unit tests for the time-driven lifecycle sweep (task 6.1, scenario "起訖時間自動推進"). No Kafka,
 * no Spring context: the {@link CampaignRepository} is stubbed and transitions are asserted against
 * the guarded edges from {@code diagrams/02-state-campaign-and-task-lifecycle.puml}.
 *
 * <p>The scheduler runs each campaign in its own transaction via a {@code TransactionTemplate}. Here
 * it is backed by a Mockito-mocked {@link PlatformTransactionManager}: the template still invokes the
 * per-campaign callback (so the transition + {@code saveAndFlush} logic genuinely runs), and on a
 * thrown exception the template rolls that one transaction back — exercising real per-campaign
 * isolation rather than a mock artifact.
 */
@ExtendWith(MockitoExtension.class)
class CampaignLifecycleSchedulerTest {

    private static final Instant PAST = Instant.now().minus(1, ChronoUnit.HOURS);
    private static final Instant FUTURE = Instant.now().plus(1, ChronoUnit.HOURS);

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CampaignLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CampaignLifecycleScheduler(campaignRepository, eventPublisher, transactionManager);
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
        verify(campaignRepository).saveAndFlush(due);
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
        verify(campaignRepository).saveAndFlush(due);
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
        verify(campaignRepository).saveAndFlush(due);
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

        verify(campaignRepository, never()).saveAndFlush(any());
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

        verify(campaignRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("per-campaign exception isolation: a saveAndFlush optimistic-lock loss rolls back only "
            + "its own transaction and the sweep continues")
    void perCampaignExceptionIsolation() {
        Campaign bad = campaign(CampaignStatus.SCHEDULED, PAST, FUTURE);
        Campaign good = campaign(CampaignStatus.SCHEDULED, PAST, FUTURE);
        when(campaignRepository.findByStatusAndStartAtLessThanEqual(eq(CampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of(bad, good));
        when(campaignRepository.findByStatusInAndEndAtLessThanEqual(ArgumentMatchers.anyCollection(), any()))
                .thenReturn(List.of());
        // Real Hibernate surfaces a concurrent operator edit as ObjectOptimisticLockingFailureException
        // on flush; saveAndFlush forces it inside the per-campaign transaction boundary (and catch).
        var badStatus = mock(TransactionStatus.class);
        var goodStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(badStatus, goodStatus);
        when(campaignRepository.saveAndFlush(bad))
                .thenThrow(new ObjectOptimisticLockingFailureException(Campaign.class, bad.getId()));

        scheduler.advanceLifecycle();

        // bad ran in its own transaction which was rolled back; good still advanced and committed in a
        // separate transaction — real per-campaign isolation, not a swallowed mock throw.
        verify(transactionManager).rollback(badStatus);
        verify(transactionManager).commit(goodStatus);
        assertThat(good.getStatus()).isEqualTo(CampaignStatus.RUNNING);
        verify(campaignRepository).saveAndFlush(good);
    }
}
