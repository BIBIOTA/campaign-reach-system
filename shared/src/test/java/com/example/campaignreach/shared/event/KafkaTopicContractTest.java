package com.example.campaignreach.shared.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Locks the Kafka topic / consumer-group / partition-key contract (design.md §9, task 2.2). These
 * are the stable cross-module identifiers; the tests pin the exact wire names and prove partition-key
 * determinism, since a drifting name or a non-deterministic key would silently break routing and the
 * idempotency that depends on it.
 */
class KafkaTopicContractTest {

    @Test
    void topicNamesMatchDesignSection9() {
        assertThat(KafkaTopics.DOMAIN_EVENTS).isEqualTo("domain.events");
        assertThat(KafkaTopics.DOMAIN_EVENTS_DLT).isEqualTo("domain.events.DLT");
        assertThat(KafkaTopics.REACH_REQUESTED).isEqualTo("reach.requested");
        assertThat(KafkaTopics.REACH_DLQ).isEqualTo("reach.dlq");
    }

    @Test
    void consumerGroupIdsMatchDesignSection9() {
        assertThat(ConsumerGroups.CAMPAIGN_TRIGGER).isEqualTo("campaign-trigger");
        assertThat(ConsumerGroups.REACH_ORCHESTRATOR).isEqualTo("reach-orchestrator");
    }

    @Test
    void domainEventKeyIsUserIdAndIsDeterministic() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        String first = PartitionKeys.forDomainEvent(userId);
        String second = PartitionKeys.forDomainEvent(userId);

        assertThat(first).isEqualTo(userId.toString());
        assertThat(first).isEqualTo(second);
    }

    @Test
    void reachRequestedKeyByRequestIdIsDeterministic() {
        UUID reachRequestId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        String first = PartitionKeys.forReachRequested(reachRequestId);
        String second = PartitionKeys.forReachRequested(reachRequestId);

        assertThat(first).isEqualTo(reachRequestId.toString());
        assertThat(first).isEqualTo(second);
    }

    @Test
    void reachRequestedCompositeKeyIsDeterministicAndNotBareCampaignId() {
        UUID campaignId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String sendCycle = "sched:11111111-1111-1111-1111-111111111111:2026-06-09T10:00";

        String first = PartitionKeys.forReachRequested(campaignId, sendCycle);
        String second = PartitionKeys.forReachRequested(campaignId, sendCycle);

        // Determinism: same inputs -> same key (idempotency depends on this).
        assertThat(first).isEqualTo(second);
        // Avoid the hot partition (NFR-002): the key must NOT be the bare campaignId.
        assertThat(first).isNotEqualTo(campaignId.toString());
        assertThat(first).isEqualTo(campaignId + ":" + sendCycle);
    }

    @Test
    void reachRequestedCompositeKeyVariesBySendCycle() {
        UUID campaignId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        String cycleA = PartitionKeys.forReachRequested(campaignId, "event:aaa");
        String cycleB = PartitionKeys.forReachRequested(campaignId, "event:bbb");

        // Different cycles of the same campaign spread across keys instead of one hot partition.
        assertThat(cycleA).isNotEqualTo(cycleB);
    }

    @Test
    void reachRequestedKeyFromEventUsesCampaignAndSendCycle() {
        UUID campaignId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var event = new ReachRequested(
                campaignId, "{}", "{}", TriggerType.SCHEDULED_BATCH, "sched:c:2026-06-09T10:00", null);

        String fromEvent = PartitionKeys.forReachRequested(event);

        assertThat(fromEvent).isEqualTo(PartitionKeys.forReachRequested(campaignId, event.sendCycle()));
        assertThat(fromEvent).isEqualTo(PartitionKeys.forReachRequested(event));
    }

    @Test
    void reachDlqReusesSourceTaskKey() {
        String sourceTaskKey = "44444444-4444-4444-4444-444444444444";

        assertThat(PartitionKeys.forReachDlq(sourceTaskKey)).isEqualTo(sourceTaskKey);
    }
}
