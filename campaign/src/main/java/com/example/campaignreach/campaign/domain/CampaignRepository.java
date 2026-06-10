package com.example.campaignreach.campaign.domain;

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
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {}
