package com.example.campaignreach.campaign.domain.coupon;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence port for the {@link CouponRedemption} entity (task 3.3).
 *
 * <p>Duplicate-redemption prevention is delegated to the {@code (coupon_code_id, user_id,
 * order_id)} unique constraint: a second insert raises {@code DataIntegrityViolationException}
 * (防同單重複核銷, FR-004). {@link #countByCouponCodeIdAndUserId(UUID, UUID)} supports per-user-limit
 * checks at the point redemption is actually wired (see package note).
 */
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

    /** Counts how many times {@code userId} has already redeemed {@code couponCodeId}. */
    long countByCouponCodeIdAndUserId(UUID couponCodeId, UUID userId);
}
