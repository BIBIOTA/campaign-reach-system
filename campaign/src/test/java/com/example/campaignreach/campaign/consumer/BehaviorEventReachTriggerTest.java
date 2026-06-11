package com.example.campaignreach.campaign.consumer;

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
import com.example.campaignreach.campaign.evaluation.BehaviorTriggerEvaluator;
import com.example.campaignreach.campaign.evaluation.ReachTriggerEvaluatorRegistry;
import com.example.campaignreach.campaign.evaluation.ScheduledTriggerEvaluator;
import com.example.campaignreach.campaign.publish.ReachRequestPublisher;
import com.example.campaignreach.shared.event.ReachRequested;
import com.example.campaignreach.shared.event.TriggerType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Fast unit tests for the behavior-event reach trigger (task 6.3, path 2; spec scenarios "行為事件觸發" and
 * "觸發判定例外隔離"). No Kafka, no Spring context, no DB: the repository and publisher are mocked and the
 * <em>real</em> {@link ReachTriggerEvaluatorRegistry} (with the real evaluators) is used so the
 * match → evaluate → publish decision, the activity-level event shape (targetSpec/reachPlan, no
 * recipients), the {@code event:{triggerEventId}} key, and per-campaign isolation are asserted directly.
 */
@ExtendWith(MockitoExtension.class)
class BehaviorEventReachTriggerTest {

    private static final Instant PAST = Instant.now().minus(2, ChronoUnit.HOURS);
    private static final Instant FUTURE = Instant.now().plus(2, ChronoUnit.HOURS);
    private static final String CART_ABANDONED = "CART_ABANDONED";

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private ReachRequestPublisher publisher;

    private ReachTriggerEvaluatorRegistry registry;
    private BehaviorEventReachTrigger trigger;

    @BeforeEach
    void setUp() {
        // Real registry + real evaluators: BehaviorTriggerEvaluator (EVENT, GIFT_ADDON) listens for
        // CART_ABANDONED; ScheduledTriggerEvaluator is registered too but never matches an EVENT context.
        registry = new ReachTriggerEvaluatorRegistry(
                List.of(new BehaviorTriggerEvaluator(), new ScheduledTriggerEvaluator()));
        trigger = new BehaviorEventReachTrigger(campaignRepository, registry, publisher);
    }

    private static Campaign running(CampaignType type) {
        return new Campaign(
                UUID.randomUUID(),
                "Promo",
                type,
                CampaignStatus.RUNNING,
                PAST,
                FUTURE,
                "{\"segment\":\"vip\"}",
                "{\"channel\":\"EMAIL\"}",
                "{}");
    }

    private static DomainBehaviorEvent event(String eventType) {
        return new DomainBehaviorEvent(UUID.randomUUID().toString(), eventType, "user-1");
    }

    @Test
    @DisplayName("行為事件觸發: CART_ABANDONED + RUNNING GIFT_ADDON → ONE EVENT ReachRequested with "
            + "event:{id} sendCycle, targetSpec/reachPlan, no recipient list")
    void cartAbandonedTriggersGiftAddonCampaign() {
        Campaign campaign = running(CampaignType.GIFT_ADDON);
        DomainBehaviorEvent evt = event(CART_ABANDONED);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(campaign));

        trigger.handle(evt);

        ArgumentCaptor<ReachRequested> captor = ArgumentCaptor.forClass(ReachRequested.class);
        verify(publisher).publish(captor.capture());
        ReachRequested out = captor.getValue();
        assertThat(out.campaignId()).isEqualTo(campaign.getId());
        assertThat(out.triggerType()).isEqualTo(TriggerType.EVENT);
        assertThat(out.triggerEventId()).isEqualTo(evt.eventId());
        assertThat(out.sendCycle()).isEqualTo("event:" + evt.eventId()).doesNotContain("recipient");
        assertThat(out.targetSpec()).isEqualTo(campaign.getTargetSpec());
        assertThat(out.reachPlan()).isEqualTo(campaign.getReachPlan());
    }

    @Test
    @DisplayName("no emission: a RUNNING DISCOUNT campaign has no EVENT evaluator → SKIPPED → no emit")
    void discountCampaignHasNoEventEvaluatorSoNoEmit() {
        Campaign campaign = running(CampaignType.DISCOUNT);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(campaign));

        trigger.handle(event(CART_ABANDONED));

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("no emission: a non-matching event type → NO_TRIGGER → no emit")
    void nonMatchingEventTypeDoesNotEmit() {
        Campaign campaign = running(CampaignType.GIFT_ADDON);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(campaign));

        trigger.handle(event("ORDER_PLACED"));

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("no emission: no RUNNING campaigns → repository empty → nothing consulted, nothing emitted")
    void noRunningCampaignsNoEmit() {
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of());

        trigger.handle(event(CART_ABANDONED));

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("觸發判定例外隔離: one campaign's publish failure does not stop emission for another matching one")
    void perCampaignIsolationOnPublishFailure() {
        Campaign bad = running(CampaignType.GIFT_ADDON);
        Campaign good = running(CampaignType.GIFT_ADDON);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(bad, good));
        doThrow(new RuntimeException("kafka down"))
                .when(publisher)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        e -> e != null && e.campaignId().equals(bad.getId())));

        trigger.handle(event(CART_ABANDONED));

        // good still emitted despite bad throwing.
        verify(publisher)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        e -> e != null && e.campaignId().equals(good.getId())));
    }

    @Test
    @DisplayName("send_cycle_key: EVENT trigger key is event:{triggerEventId}")
    void eventSendCycleKey() {
        assertThat(BehaviorEventReachTrigger.sendCycleKey("evt-42")).isEqualTo("event:evt-42");
    }
}
