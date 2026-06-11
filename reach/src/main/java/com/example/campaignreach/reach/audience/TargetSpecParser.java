package com.example.campaignreach.reach.audience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses the event-carried {@code targetSpec} JSON string into a validated {@link TargetSpec} at the
 * reach trust boundary (spec §4, FR-007/FR-013).
 *
 * <p>The {@code ReachRequested} event freezes {@code targetSpec} as a raw JSON snapshot. This is the
 * single place reach turns that untrusted string into a typed model, so all shape validation lives
 * here: unknown/blank {@code kind}, a {@code STATIC_LIST} without {@code listId}, or a
 * {@code CONDITION} without {@code conditions} are rejected with a clear {@link
 * IllegalArgumentException} rather than failing deeper in resolution.
 *
 * <p>Accepted shapes:
 *
 * <pre>{@code
 * {"kind":"STATIC_LIST","listId":"<uuid>","conditions":{}}
 * {"kind":"CONDITION","listId":null,"conditions":{"memberLevel":"GOLD","region":"TW"}}
 * }</pre>
 */
@Component
public class TargetSpecParser {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper the application JSON mapper (defensively copied)
     */
    public TargetSpecParser(ObjectMapper objectMapper) {
        // Defensive copy: parsing must not be perturbed by later reconfiguration of the shared
        // application ObjectMapper, and the parser must not expose/mutate that shared instance.
        this.objectMapper = objectMapper.copy();
    }

    /**
     * Parses and validates a {@code targetSpec} JSON snapshot.
     *
     * @param json the raw {@code targetSpec} JSON the event froze
     * @return the validated reach-side {@link TargetSpec}
     * @throws IllegalArgumentException when the JSON is blank, malformed, or violates the kind shape
     */
    public TargetSpec parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("targetSpec JSON must not be null or blank");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("targetSpec JSON is malformed: " + e.getOriginalMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("targetSpec JSON must be a JSON object");
        }

        TargetSpec.Kind kind = parseKind(root.path("kind"));
        UUID listId = parseListId(root.path("listId"));
        Map<String, Object> conditions = parseConditions(root.path("conditions"));

        // Invariants (STATIC_LIST needs listId; CONDITION needs conditions) are enforced by TargetSpec.
        return new TargetSpec(kind, listId, conditions);
    }

    private static TargetSpec.Kind parseKind(JsonNode kindNode) {
        String raw = kindNode.isTextual() ? kindNode.asText().trim() : "";
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("targetSpec.kind must not be blank");
        }
        try {
            return TargetSpec.Kind.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown targetSpec.kind: " + raw, e);
        }
    }

    private static UUID parseListId(JsonNode listIdNode) {
        if (listIdNode.isMissingNode() || listIdNode.isNull()) {
            return null;
        }
        String raw = listIdNode.asText().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("targetSpec.listId is not a valid UUID: " + raw, e);
        }
    }

    private Map<String, Object> parseConditions(JsonNode conditionsNode) {
        if (conditionsNode.isMissingNode() || conditionsNode.isNull()) {
            return Map.of();
        }
        if (!conditionsNode.isObject()) {
            throw new IllegalArgumentException("targetSpec.conditions must be a JSON object");
        }
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditionsNode
                .fields()
                .forEachRemaining(entry ->
                        conditions.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
        return conditions;
    }
}
