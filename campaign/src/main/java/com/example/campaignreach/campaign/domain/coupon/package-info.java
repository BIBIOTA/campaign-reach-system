/**
 * Campaign module — coupon domain (three-table model: {@code coupon_campaign} /
 * {@code coupon_code} / {@code coupon_redemption}; task 3.3, FR-002/FR-004).
 *
 * <p>Holds the coupon entities, their {@link com.example.campaignreach.campaign.domain.coupon.CodeType}
 * / {@link com.example.campaignreach.campaign.domain.coupon.CouponCodeStatus} enums, and the JPA
 * repository ports. The two core usage guarantees live at the persistence layer:
 *
 * <ul>
 *   <li><b>Total-volume control (FR-004)</b> — the atomic conditional UPDATE
 *       {@code CouponCampaignRepository.tryIncrementUsedCount} keeps {@code usedCount} from ever
 *       exceeding {@code totalUsageLimit}.</li>
 *   <li><b>Duplicate-redemption prevention (FR-004)</b> — the {@code (coupon_code_id, user_id,
 *       order_id)} unique key blocks a second redemption of the same code by the same user on the
 *       same order.</li>
 * </ul>
 *
 * <p>Scope note (task 3.3): this is the domain/persistence layer only. {@code per_user_limit}
 * enforcement is a counting check ({@code CouponRedemptionRepository.countByCouponCodeIdAndUserId})
 * that belongs in the redemption flow wired by a later section (REST API / redemption service); it
 * is intentionally not invoked here because no redemption endpoint exists yet.
 *
 * <p>This package MUST NOT be imported by the reach module (enforced by ArchUnit).
 */
package com.example.campaignreach.campaign.domain.coupon;
