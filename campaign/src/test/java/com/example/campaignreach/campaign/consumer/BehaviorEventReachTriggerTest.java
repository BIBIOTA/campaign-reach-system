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
    @DisplayName("觸發判定例外隔離: one campaign's DETERMINATION failure is isolated; another matching campaign "
            + "still emits and handle() does not throw")
    void determinationFailureIsIsolated() {
        // A mocked registry lets us force a determination throw for the first campaign; the registry
        // normally turns this into SKIPPED itself, so the broad catch in decideEmission is the safety net.
        ReachTriggerEvaluatorRegistry mockRegistry = org.mockito.Mockito.mock(ReachTriggerEvaluatorRegistry.class);
        BehaviorEventReachTrigger isolatingTrigger =
                new BehaviorEventReachTrigger(campaignRepository, mockRegistry, publisher);
        Campaign bad = running(CampaignType.GIFT_ADDON);
        Campaign good = running(CampaignType.GIFT_ADDON);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(bad, good));
        when(mockRegistry.evaluate(any()))
                .thenThrow(new RuntimeException("evaluator blew up"))
                .thenReturn(com.example.campaignreach.campaign.evaluation.TriggerDecision.of(true));

        isolatingTrigger.handle(event(CART_ABANDONED));

        // good still emitted despite bad's determination throwing; no exception escaped handle().
        verify(publisher)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        e -> e != null && e.campaignId().equals(good.getId())));
    }

    @Test
    @DisplayName("at-least-once: a PUBLISH failure propagates out of handle() (so the consumer skips the ack "
            + "and Kafka re-delivers) — it is NOT swallowed")
    void publishFailurePropagates() {
        Campaign bad = running(CampaignType.GIFT_ADDON);
        Campaign good = running(CampaignType.GIFT_ADDON);
        when(campaignRepository.findByStatus(CampaignStatus.RUNNING)).thenReturn(List.of(bad, good));
        doThrow(new IllegalStateException("kafka down"))
                .when(publisher)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        e -> e != null && e.campaignId().equals(bad.getId())));

        DomainBehaviorEvent evt = event(CART_ABANDONED);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> trigger.handle(evt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kafka down");

        // The failed publish aborts the batch and propagates; the later campaign is not emitted on this
        // attempt (re-delivery reprocesses the whole event, deduped downstream by reach's unique key).
        verify(publisher, never())
                .publish(org.mockito.ArgumentMatchers.argThat(
                        e -> e != null && e.campaignId().equals(good.getId())));
    }

    @Test
    @DisplayName("send_cycle_key: EVENT trigger key is event:{triggerEventId}")
    void eventSendCycleKey() {
        assertThat(BehaviorEventReachTrigger.sendCycleKey("evt-42")).isEqualTo("event:evt-42");
    }
}
