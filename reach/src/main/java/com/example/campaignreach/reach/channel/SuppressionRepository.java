package com.example.campaignreach.reach.channel;

import com.example.campaignreach.shared.event.Channel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence port for {@link SuppressionEntry} — the pre-send suppression-list lookup keyed on
 * {@code (user_id, channel)} (design.md §10; FR-015).
 */
public interface SuppressionRepository extends JpaRepository<SuppressionEntry, SuppressionEntryId> {

    /**
     * Looks up the suppression entry for a recipient on a specific channel.
     *
     * @param userId the recipient's upstream member id
     * @param channel the channel the send would use (suppression is channel-scoped)
     * @return the matching entry, or empty when the recipient is not suppressed on that channel
     */
    Optional<SuppressionEntry> findByUserIdAndChannel(UUID userId, Channel channel);
}
