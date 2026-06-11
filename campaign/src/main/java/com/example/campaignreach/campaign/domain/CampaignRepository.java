package com.example.campaignreach.campaign.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence port for the {@link Campaign} aggregate (task 3.1).
 *
 * <p>Optimistic locking is provided transparently by the {@code @Version} field on
 * {@link Campaign}: a save against a stale version raises
 * {@code ObjectOptimisticLockingFailureException} so the later writer fails rather
 * than silently overwriting (FR-001).
 */
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    /**
     * Campaigns in the given status whose {@code startAt} is at or before {@code now} — the
     * candidates the lifecycle scheduler auto-advances to {@code RUNNING} (task 6.1, FR-012).
     */
    List<Campaign> findByStatusAndStartAtLessThanEqual(CampaignStatus status, Instant now);

    /**
     * Campaigns in any of the given statuses whose {@code endAt} is at or before {@code now} — the
     * candidates the lifecycle scheduler auto-advances to {@code ENDED} (task 6.1, FR-012).
     */
    List<Campaign> findByStatusInAndEndAtLessThanEqual(Collection<CampaignStatus> statuses, Instant now);
}
