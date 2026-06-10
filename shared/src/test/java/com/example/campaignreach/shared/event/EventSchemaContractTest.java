package com.example.campaignreach.shared.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Locks the Kafka event-schema wire contract for the cross-module {@code shared/event} types
 * (design.md §5, task 2.1). These are the stable contracts campaign and reach communicate through;
 * the tests pin field names, the camelCase {@code sendCycle} acceptance, the enum literals, and the
 * "no recipient list" rule for the activity-level {@link ReachRequested}.
 *
 * <p>Jackson is used here because Spring Kafka's JSON serializer (wired in task 2.2) maps these
 * records to/from JSON; verifying the mapper-default field names now prevents wire drift later.
 */
class EventSchemaContractTest {

    // Mirrors production: Spring Boot registers all discovered Jackson modules
    // (including JavaTimeModule) so Instant serializes as ISO-8601, not a numeric array.
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void reachRequestedRoundTripsThroughJackson() throws Exception {
        var event = new ReachRequested(
                UUID.randomUUID(),
                "{\"segment\":\"vip\"}",
                "{\"channel\":\"EMAIL\"}",
                TriggerType.SCHEDULED_BATCH,
                "sched:11111111-1111-1111-1111-111111111111:2026-06-09T10:00",
                null);

        var restored = mapper.readValue(mapper.writeValueAsString(event), ReachRequested.class);

        assertThat(restored).isEqualTo(event);
    }

    @Test
    void reachRequestedCarriesActivityLevelFieldsAndNoRecipientList() throws Exception {
        var event =
                new ReachRequested(UUID.randomUUID(), "target", "plan", TriggerType.EVENT, "event:evt-42", "evt-42");

        JsonNode json = mapper.readTree(mapper.writeValueAsString(event));

        // Carries exactly the activity-level identification fields (§5).
        assertThat(json.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "campaignId", "targetSpec", "reachPlan", "triggerType", "sendCycle", "triggerEventId");
        // Activity-level event MUST NOT carry the full recipient list.
        assertThat(json.has("recipients")).isFalse();
        assertThat(json.has("userId")).isFalse();
        assertThat(json.has("userIds")).isFalse();
    }

    @Test
    void reachRequestedWireFieldIsCamelCaseSendCycle() throws Exception {
        var event = new ReachRequested(
                UUID.randomUUID(), "target", "plan", TriggerType.SCHEDULED_BATCH, "sched:c:2026-06-09T10:00", null);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(event));

        // The acceptance: payload uses camelCase `sendCycle`; same value as persisted
        // `send_cycle_key`, only naming style differs. The snake_case form must NOT appear.
        assertThat(json.has("sendCycle")).isTrue();
        assertThat(json.has("send_cycle_key")).isFalse();
        assertThat(json.has("send_cycle")).isFalse();
        assertThat(json.get("sendCycle").asText()).isEqualTo("sched:c:2026-06-09T10:00");
    }

    @Test
    void triggerTypeSerializesAsScheduledBatchOrEvent() throws Exception {
        var scheduled = new ReachRequested(UUID.randomUUID(), "t", "p", TriggerType.SCHEDULED_BATCH, "sched:x", null);
        var behaviour = new ReachRequested(UUID.randomUUID(), "t", "p", TriggerType.EVENT, "event:y", "y");

        assertThat(mapper.readTree(mapper.writeValueAsString(scheduled))
                        .get("triggerType")
                        .asText())
                .isEqualTo("SCHEDULED_BATCH");
        assertThat(mapper.readTree(mapper.writeValueAsString(behaviour))
                        .get("triggerType")
                        .asText())
                .isEqualTo("EVENT");
    }

    @Test
    void reachTaskCreatedRoundTripsAndUsesCamelCaseSendCycle() throws Exception {
        var event = new ReachTaskCreated(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "sched:c:2026-06-09T10:00",
                Channel.EMAIL);

        String wire = mapper.writeValueAsString(event);
        JsonNode json = mapper.readTree(wire);

        assertThat(mapper.readValue(wire, ReachTaskCreated.class)).isEqualTo(event);
        assertThat(json.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "reachTaskId", "reachRequestId", "campaignId", "userId", "sendCycle", "channel");
        assertThat(json.has("send_cycle_key")).isFalse();
        assertThat(json.get("channel").asText()).isEqualTo("EMAIL");
    }

    @Test
    void sendResultRecordedRoundTripsThroughJackson() throws Exception {
        var event = new SendResultRecorded(
                UUID.randomUUID(), "provider-msg-1", "SENT", Instant.parse("2026-06-09T10:00:00Z"));

        String wire = mapper.writeValueAsString(event);
        JsonNode json = mapper.readTree(wire);

        assertThat(mapper.readValue(wire, SendResultRecorded.class)).isEqualTo(event);
        assertThat(json.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("reachTaskId", "providerMessageId", "outcome", "occurredAt");
    }

    // --- Compact-constructor validation: invalid events MUST fail at construction, before they can
    // be serialized onto the wire (so a malformed event never reaches a consumer's deserializer). ---

    @Test
    void reachRequestedRejectsBlankRequiredFields() {
        assertThatThrownBy(() -> new ReachRequested(null, "t", "p", TriggerType.SCHEDULED_BATCH, "sched:c", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new ReachRequested(UUID.randomUUID(), " ", "p", TriggerType.SCHEDULED_BATCH, "sched:c", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetSpec");
        assertThatThrownBy(() ->
                        new ReachRequested(UUID.randomUUID(), "t", "", TriggerType.SCHEDULED_BATCH, "sched:c", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reachPlan");
        assertThatThrownBy(
                        () -> new ReachRequested(UUID.randomUUID(), "t", "p", TriggerType.SCHEDULED_BATCH, " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sendCycle");
    }

    @Test
    void reachRequestedRequiresTriggerEventIdOnlyForEventTrigger() {
        // EVENT trigger: triggerEventId is mandatory.
        assertThatThrownBy(() -> new ReachRequested(UUID.randomUUID(), "t", "p", TriggerType.EVENT, "event:e", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("triggerEventId");
        assertThatThrownBy(() -> new ReachRequested(UUID.randomUUID(), "t", "p", TriggerType.EVENT, "event:e", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("triggerEventId");
        // SCHEDULED_BATCH: null triggerEventId is valid (no behavior-event source).
        assertThat(new ReachRequested(UUID.randomUUID(), "t", "p", TriggerType.SCHEDULED_BATCH, "sched:c", null)
                        .triggerEventId())
                .isNull();
    }

    @Test
    void reachTaskCreatedRejectsNullIdsAndBlankSendCycle() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new ReachTaskCreated(null, id, id, id, "sched:c", Channel.EMAIL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReachTaskCreated(id, id, id, id, " ", Channel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sendCycle");
        assertThatThrownBy(() -> new ReachTaskCreated(id, id, id, id, "sched:c", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sendResultRecordedRejectsBlankOutcomeAndNullTimestamp() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-09T10:00:00Z");
        assertThatThrownBy(() -> new SendResultRecorded(null, "m", "SENT", now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SendResultRecorded(id, "m", " ", now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
        assertThatThrownBy(() -> new SendResultRecorded(id, "m", "SENT", null))
                .isInstanceOf(NullPointerException.class);
        // providerMessageId is nullable (provider may return none on early failure).
        assertThat(new SendResultRecorded(id, null, "FAILED", now).providerMessageId())
                .isNull();
    }
}
