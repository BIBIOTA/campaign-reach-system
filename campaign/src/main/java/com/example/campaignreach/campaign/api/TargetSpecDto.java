package com.example.campaignreach.campaign.api;

import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Audience targeting request/view payload (class-model {@code TargetSpec}).
 *
 * <p>A {@link Kind#STATIC_LIST} target points at a pre-built {@code listId}; a {@link
 * Kind#CONDITION} target carries an attribute {@code conditions} map. There is no per-type validator
 * yet (FR-007 scope), so this DTO is persisted as a raw JSON string on the {@code target_spec}
 * column — it stays strongly typed on the wire per the class diagram.
 *
 * @param kind whether the audience is a static list or an attribute condition
 * @param listId the pre-built list id; expected when {@code kind == STATIC_LIST}
 * @param conditions attribute conditions; expected when {@code kind == CONDITION}
 */
public record TargetSpecDto(
        @NotNull(message = "targetSpec.kind must not be null") Kind kind, UUID listId, Map<String, Object> conditions) {

    /** Defensively copies {@code conditions} to an immutable map so the spec cannot be mutated post-construction. */
    public TargetSpecDto {
        conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
    }

    /** Returns the (already immutable) conditions as an unmodifiable view so callers cannot mutate the spec. */
    @Override
    public Map<String, Object> conditions() {
        return Collections.unmodifiableMap(conditions);
    }

    /** Targeting strategy (class-model {@code TargetSpec.kind}). */
    public enum Kind {
        STATIC_LIST,
        CONDITION
    }
}
