package com.example.campaignreach.campaign.domain.coupon;

/**
 * Lifecycle status of an individual coupon code (ER {@code coupon_code_status};
 * class-model {@code CouponCodeStatus}).
 *
 * <p>Spec §4: AVAILABLE / ASSIGNED / REDEEMED / EXPIRED.
 */
public enum CouponCodeStatus {
    AVAILABLE,
    ASSIGNED,
    REDEEMED,
    EXPIRED
}
