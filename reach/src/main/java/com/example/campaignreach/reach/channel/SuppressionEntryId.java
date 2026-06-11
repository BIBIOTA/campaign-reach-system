package com.example.campaignreach.reach.channel;

import com.example.campaignreach.shared.event.Channel;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite identity for {@link SuppressionEntry} — the {@code (user_id, channel)} primary key of ER
 * {@code suppression} (design.md §10; FR-015).
 *
 * <p>JPA {@code @IdClass} requires a serializable key type with {@code equals}/{@code hashCode} over
 * the id fields, and a no-arg constructor.
 */
public class SuppressionEntryId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;
    private Channel channel;

    protected SuppressionEntryId() {}

    public SuppressionEntryId(UUID userId, Channel channel) {
        this.userId = userId;
        this.channel = channel;
    }

    public UUID getUserId() {
        return userId;
    }

    public Channel getChannel() {
        return channel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SuppressionEntryId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && channel == other.channel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, channel);
    }
}
