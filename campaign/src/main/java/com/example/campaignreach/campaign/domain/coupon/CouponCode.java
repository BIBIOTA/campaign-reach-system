package com.example.campaignreach.campaign.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An individual coupon code — the middle tier of the coupon model (ER {@code coupon_code};
 * class-model {@code CouponCode}; FR-002).
 *
 * <p>A SHARED_CODE campaign owns exactly one code; a UNIQUE_CODE campaign owns many, each
 * optionally pinned to an {@code assignedUserId} (一人一碼). The {@code code} carries a
 * case-insensitive unique index ({@code lower(code)}) enforced at the schema level.
 */
@Entity
@Table(name = "coupon_code")
public class CouponCode {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "coupon_campaign_id", nullable = false)
    private UUID couponCampaignId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "coupon_code_status")
    private CouponCodeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** JPA requires a no-arg constructor. */
    protected CouponCode() {}

    /**
     * Creates a coupon code in its initial persistent state.
     *
     * @param assignedUserId the pre-assigned user for a UNIQUE_CODE (一人一碼); {@code null} for a
     *     SHARED_CODE or an unassigned pool code
     */
    public CouponCode(
            UUID id,
            UUID couponCampaignId,
            String code,
            UUID assignedUserId,
            CouponCodeStatus status,
            Instant createdAt) {
        this.id = id;
        this.couponCampaignId = couponCampaignId;
        this.code = code;
        this.assignedUserId = assignedUserId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCouponCampaignId() {
        return couponCampaignId;
    }

    public String getCode() {
        return code;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public CouponCodeStatus getStatus() {
        return status;
    }

    public void setStatus(CouponCodeStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
