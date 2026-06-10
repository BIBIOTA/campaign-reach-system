package com.example.campaignreach.campaign.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Coupon campaign — the top tier of the three-table coupon model (ER {@code coupon_campaign};
 * class-model {@code CouponCampaign}; FR-002/FR-004).
 *
 * <p>Holds the usage rules for a {@code Campaign}'s coupons: the distribution {@link CodeType},
 * the optional {@code sharedCode} (SHARED_CODE only), and the {@code totalUsageLimit} /
 * {@code perUserLimit} / {@code usedCount} counters. Total-volume control is enforced via the
 * atomic increment {@code CouponCampaignRepository.tryIncrementUsedCount} which never lets
 * {@code usedCount} exceed {@code totalUsageLimit} (design.md §4: "used_count atomic update 控總量").
 */
@Entity
@Table(name = "coupon_campaign")
public class CouponCampaign {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "code_type", nullable = false, columnDefinition = "code_type")
    private CodeType codeType;

    @Column(name = "shared_code")
    private String sharedCode;

    @Column(name = "total_usage_limit", nullable = false)
    private int totalUsageLimit;

    @Column(name = "per_user_limit", nullable = false)
    private int perUserLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    /** JPA requires a no-arg constructor. */
    protected CouponCampaign() {}

    /**
     * Creates a coupon campaign in its initial persistent state ({@code usedCount} starts at 0).
     *
     * @param sharedCode the shared redemption code for {@link CodeType#SHARED_CODE}; {@code null}
     *     for {@link CodeType#UNIQUE_CODE} (per-user codes live in {@link CouponCode})
     */
    public CouponCampaign(
            UUID id, UUID campaignId, CodeType codeType, String sharedCode, int totalUsageLimit, int perUserLimit) {
        this.id = id;
        this.campaignId = campaignId;
        this.codeType = codeType;
        this.sharedCode = sharedCode;
        this.totalUsageLimit = totalUsageLimit;
        this.perUserLimit = perUserLimit;
        this.usedCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public CodeType getCodeType() {
        return codeType;
    }

    public String getSharedCode() {
        return sharedCode;
    }

    public int getTotalUsageLimit() {
        return totalUsageLimit;
    }

    public int getPerUserLimit() {
        return perUserLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }
}
