package com.example.campaignreach.campaign.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A single redemption event — the bottom tier of the coupon model (ER {@code coupon_redemption};
 * class-model {@code CouponRedemption}; FR-004).
 *
 * <p>The {@code (coupon_code_id, user_id, order_id)} unique key blocks the same user from
 * redeeming the same code on the same order twice: a second insert fails with
 * {@code DataIntegrityViolationException} (防同單重複核銷).
 */
@Entity
@Table(
        name = "coupon_redemption",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "ux_coupon_redemption_code_user_order",
                        columnNames = {"coupon_code_id", "user_id", "order_id"}))
public class CouponRedemption {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "coupon_code_id", nullable = false)
    private UUID couponCodeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    /** JPA requires a no-arg constructor. */
    protected CouponRedemption() {}

    /** Records a redemption of {@code couponCodeId} by {@code userId} on {@code orderId}. */
    public CouponRedemption(UUID id, UUID couponCodeId, UUID userId, UUID orderId, Instant redeemedAt) {
        this.id = id;
        this.couponCodeId = couponCodeId;
        this.userId = userId;
        this.orderId = orderId;
        this.redeemedAt = redeemedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCouponCodeId() {
        return couponCodeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }
}
