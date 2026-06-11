package com.example.campaignreach.reach.audience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Membership row of a static {@code audience_list} (ER {@code audience_list_member}; diagram
 * {@code 05-er-database-schema.puml} block A; spec §4, FR-007/FR-013).
 *
 * <p>Read-only lookup entity for {@link TargetSpec.Kind#STATIC_LIST} resolution: the composite key is
 * {@code (list_id, user_id)} (mapped via {@code @IdClass} {@link AudienceListMemberId}), with
 * {@code list_id} a foreign key to {@code audience_list}. Only {@code user_id} is surfaced to a
 * {@link Recipient} (PII minimization, NFR-005).
 *
 * <p>This entity does not throw in its constructor (the rows are curated lookup data, not a domain
 * aggregate with creation invariants), so no {@code CT_CONSTRUCTOR_THROW} SpotBugs exclusion is
 * needed.
 */
@Entity
@Table(name = "audience_list_member")
@IdClass(AudienceListMemberId.class)
public class AudienceListMember {

    @Id
    @Column(name = "list_id", nullable = false, updatable = false)
    private UUID listId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "added_at")
    private Instant addedAt;

    /** JPA requires a no-arg constructor. */
    protected AudienceListMember() {}

    public UUID getListId() {
        return listId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
