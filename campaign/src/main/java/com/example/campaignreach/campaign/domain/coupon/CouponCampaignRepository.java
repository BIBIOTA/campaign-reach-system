package com.example.campaignreach.campaign.domain.coupon;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence port for the {@link CouponCampaign} aggregate (task 3.3).
 *
 * <p>Total-volume control (FR-004) is enforced by {@link #tryIncrementUsedCount(UUID)}: a single
 * conditional UPDATE bumps {@code usedCount} only while it is still below {@code totalUsageLimit},
 * so the database — not the application — guarantees the counter never exceeds the limit even under
 * concurrent redemptions. The affected-row count (1 = reserved, 0 = limit reached) is the decision.
 */
public interface CouponCampaignRepository extends JpaRepository<CouponCampaign, UUID> {

    /**
     * Atomically reserves one usage slot. Returns 1 if {@code usedCount} was below
     * {@code totalUsageLimit} and got incremented, or 0 if the limit was already reached (the
     * caller must then reject the redemption). This is the design-mandated "used_count atomic
     * update 控總量" approach (design.md §4) — no Redis (out of MVP scope).
     *
     * <p>{@code @Transactional} (default propagation {@code REQUIRED}) makes this modifying query
     * a self-contained unit of work: it joins the caller's transaction when the future redemption
     * flow wraps it together with the redemption insert, and opens its own when called standalone —
     * the single conditional UPDATE keeps the volume guarantee either way.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponCampaign c SET c.usedCount = c.usedCount + 1 "
            + "WHERE c.id = :id AND c.usedCount < c.totalUsageLimit")
    int tryIncrementUsedCount(@Param("id") UUID id);
}
