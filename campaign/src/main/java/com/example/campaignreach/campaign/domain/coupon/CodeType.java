package com.example.campaignreach.campaign.domain.coupon;

/**
 * Coupon code distribution strategy (ER {@code code_type}; class-model {@code CodeType}).
 *
 * <p>Spec §4 / FR-002: {@code SHARED_CODE} (one code shared by everyone) or
 * {@code UNIQUE_CODE} (one code assigned per user — 一人一碼).
 */
public enum CodeType {
    SHARED_CODE,
    UNIQUE_CODE
}
