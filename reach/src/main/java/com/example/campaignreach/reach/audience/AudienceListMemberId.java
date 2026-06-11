package com.example.campaignreach.reach.audience;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite identity for {@link AudienceListMember} — the {@code (list_id, user_id)} primary key of
 * ER {@code audience_list_member} (diagram {@code 05-er-database-schema.puml} block A).
 *
 * <p>JPA {@code @IdClass} requires a serializable key type with {@code equals}/{@code hashCode} over
 * the id fields, and a no-arg constructor.
 */
public class AudienceListMemberId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID listId;
    private UUID userId;

    protected AudienceListMemberId() {}

    public AudienceListMemberId(UUID listId, UUID userId) {
        this.listId = listId;
        this.userId = userId;
    }

    public UUID getListId() {
        return listId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AudienceListMemberId other)) {
            return false;
        }
        return Objects.equals(listId, other.listId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listId, userId);
    }
}
