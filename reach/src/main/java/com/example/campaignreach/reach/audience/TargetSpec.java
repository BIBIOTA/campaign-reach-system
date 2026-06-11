package com.example.campaignreach.reach.audience;

import java.util.Map;
import java.util.UUID;

/**
 * Reach-owned view of a campaign's targeting condition (class-model {@code TargetSpec}; spec §4,
 * FR-007/FR-013).
 *
 * <p>Reach deliberately keeps its <strong>own</strong> {@code TargetSpec} model rather than importing
 * the campaign module's DTO: the two modules communicate only through the shared event kernel
 * (module-boundary rule, ArchUnit-enforced). This type is the parsed, validated shape of the
 * {@code targetSpec} JSON the {@code ReachRequested} event carries — produced at the reach trust
 * boundary by {@link TargetSpecParser}.
 *
 * <p>Two kinds are supported in the MVP (design.md §4):
 *
 * <ul>
 *   <li>{@link Kind#STATIC_LIST} — a curated {@code audience_list}; {@code listId} is required.
 *   <li>{@link Kind#CONDITION} — simple member-attribute segmentation (member level / region);
 *       {@code conditions} is required and {@code listId} is absent.
 * </ul>
 *
 * <p>Adding a future kind (e.g. a richer tag system) means adding a strategy in
 * {@code AudienceResolver}, not editing this type (OCP).
 *
 * @param kind the targeting strategy discriminator
 * @param listId the static-list id ({@code STATIC_LIST} only; otherwise {@code null})
 * @param conditions the segmentation conditions ({@code CONDITION} only; otherwise empty)
 */
public record TargetSpec(Kind kind, UUID listId, Map<String, Object> conditions) {

    /** Targeting strategy discriminator carried by the event's {@code targetSpec} JSON. */
    public enum Kind {
        STATIC_LIST,
        CONDITION
    }

    /** Normalizes {@code conditions} to an immutable copy and enforces the per-kind invariants. */
    public TargetSpec {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
        if (kind == Kind.STATIC_LIST && listId == null) {
            throw new IllegalArgumentException("STATIC_LIST targetSpec requires a listId");
        }
        if (kind == Kind.CONDITION && conditions.isEmpty()) {
            throw new IllegalArgumentException("CONDITION targetSpec requires at least one condition");
        }
    }
}
