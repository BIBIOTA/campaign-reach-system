package com.example.campaignreach.campaign.consumer;

import static org.mockito.Mockito.inOrder;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Unit test for the thin {@code @KafkaListener} adapter (task 6.3, at-least-once §9). Verifies the
 * adapter delegates to the handler and then acknowledges — so the offset commits only after the event
 * has been processed (its ReachRequested handed off to the publisher), never before.
 */
@ExtendWith(MockitoExtension.class)
class DomainEventConsumerTest {

    @Mock
    private BehaviorEventReachTrigger reachTrigger;

    @Mock
    private Acknowledgment acknowledgment;

    @Test
    @DisplayName("at-least-once: adapter handles the event THEN acknowledges (ack after processing)")
    void handlesThenAcks() {
        DomainEventConsumer consumer = new DomainEventConsumer(reachTrigger);
        DomainBehaviorEvent event = new DomainBehaviorEvent(UUID.randomUUID().toString(), "CART_ABANDONED", "user-1");

        consumer.onDomainEvent(event, acknowledgment);

        InOrder order = inOrder(reachTrigger, acknowledgment);
        order.verify(reachTrigger).handle(event);
        order.verify(acknowledgment).acknowledge();
    }
}
