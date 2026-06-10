package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.campaign.domain.CampaignType;
import com.example.campaignreach.campaign.domain.CurrentOperator;
import com.example.campaignreach.campaign.domain.coupon.CodeType;
import com.example.campaignreach.campaign.domain.coupon.CouponCampaign;
import com.example.campaignreach.campaign.domain.coupon.CouponCampaignRepository;
import com.example.campaignreach.campaign.domain.coupon.CouponCode;
import com.example.campaignreach.campaign.domain.coupon.CouponCodeRepository;
import com.example.campaignreach.campaign.domain.coupon.CouponCodeStatus;
import com.example.campaignreach.campaign.domain.coupon.CouponRedemption;
import com.example.campaignreach.campaign.domain.coupon.CouponRedemptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Persistence-level integration test for the coupon three-table model (task 3.3), backed by a real
 * PostgreSQL container with the Flyway-owned schema applied. Auto-skipped without Docker via the
 * inherited {@link RequiresDocker} condition.
 *
 * <p>Verifies the spec §4 / FR-002/FR-004 scenarios: SHARED_CODE vs UNIQUE_CODE storage, the
 * duplicate-redemption unique key, and the atomic {@code used_count} total-volume control.
 */
class CouponPersistenceIntegrationTest extends AbstractIntegrationTest {

    private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CouponCampaignRepository couponCampaignRepository;

    @Autowired
    private CouponCodeRepository couponCodeRepository;

    @Autowired
    private CouponRedemptionRepository couponRedemptionRepository;

    @AfterEach
    void clearOperator() {
        CurrentOperator.clear();
    }

    private UUID newCampaignId() {
        CurrentOperator.set(OPERATOR);
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Campaign campaign = new Campaign(
                UUID.randomUUID(),
                "Coupon Sale",
                CampaignType.DISCOUNT,
                CampaignStatus.DRAFT,
                start,
                start.plus(7, ChronoUnit.DAYS),
                "{}",
                "{}",
                "{}");
        return campaignRepository.saveAndFlush(campaign).getId();
    }

    /**
     * Scenario: 設定共用碼與一人一碼 (SHARED_CODE) — a SHARED_CODE campaign persists its
     * total_usage_limit / per_user_limit / used_count and exactly one coupon_code.
     */
    @Test
    void sharedCodeCampaignPersistsLimitsAndSingleCode() {
        UUID campaignId = newCampaignId();
        CouponCampaign cc = couponCampaignRepository.saveAndFlush(
                new CouponCampaign(UUID.randomUUID(), campaignId, CodeType.SHARED_CODE, "SAVE10", 100, 1));

        CouponCampaign reread = couponCampaignRepository.findById(cc.getId()).orElseThrow();
        assertThat(reread.getCodeType()).isEqualTo(CodeType.SHARED_CODE);
        assertThat(reread.getSharedCode()).isEqualTo("SAVE10");
        assertThat(reread.getTotalUsageLimit()).isEqualTo(100);
        assertThat(reread.getPerUserLimit()).isEqualTo(1);
        assertThat(reread.getUsedCount()).isZero();

        CouponCode code = couponCodeRepository.saveAndFlush(new CouponCode(
                UUID.randomUUID(), cc.getId(), "SAVE10", null, CouponCodeStatus.AVAILABLE, Instant.now()));
        CouponCode rereadCode = couponCodeRepository.findById(code.getId()).orElseThrow();
        assertThat(rereadCode.getAssignedUserId()).isNull();
        assertThat(rereadCode.getStatus()).isEqualTo(CouponCodeStatus.AVAILABLE);
    }

    /**
     * Scenario: 設定共用碼與一人一碼 (UNIQUE_CODE) — a UNIQUE_CODE campaign persists multiple
     * coupon_code rows, each with an assigned_user_id and ASSIGNED status (一人一碼).
     */
    @Test
    void uniqueCodeCampaignPersistsMultipleAssignedCodes() {
        UUID campaignId = newCampaignId();
        CouponCampaign cc = couponCampaignRepository.saveAndFlush(
                new CouponCampaign(UUID.randomUUID(), campaignId, CodeType.UNIQUE_CODE, null, 2, 1));

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        CouponCode codeA = couponCodeRepository.saveAndFlush(new CouponCode(
                UUID.randomUUID(), cc.getId(), "UNIQ-A", userA, CouponCodeStatus.ASSIGNED, Instant.now()));
        CouponCode codeB = couponCodeRepository.saveAndFlush(new CouponCode(
                UUID.randomUUID(), cc.getId(), "UNIQ-B", userB, CouponCodeStatus.ASSIGNED, Instant.now()));

        assertThat(couponCodeRepository.findById(codeA.getId()).orElseThrow().getAssignedUserId())
                .isEqualTo(userA);
        assertThat(couponCodeRepository.findById(codeB.getId()).orElseThrow().getAssignedUserId())
                .isEqualTo(userB);
        assertThat(couponCodeRepository.findById(codeB.getId()).orElseThrow().getStatus())
                .isEqualTo(CouponCodeStatus.ASSIGNED);
    }

    /**
     * Scenario: 防同單重複核銷 — a second coupon_redemption with the same
     * (coupon_code_id, user_id, order_id) is blocked by the unique key.
     */
    @Test
    void duplicateRedemptionOnSameCodeUserOrderIsBlocked() {
        UUID campaignId = newCampaignId();
        CouponCampaign cc = couponCampaignRepository.saveAndFlush(
                new CouponCampaign(UUID.randomUUID(), campaignId, CodeType.SHARED_CODE, "DUP", 100, 5));
        CouponCode code = couponCodeRepository.saveAndFlush(
                new CouponCode(UUID.randomUUID(), cc.getId(), "DUP", null, CouponCodeStatus.AVAILABLE, Instant.now()));

        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        couponRedemptionRepository.saveAndFlush(
                new CouponRedemption(UUID.randomUUID(), code.getId(), userId, orderId, Instant.now()));

        assertThatThrownBy(() -> couponRedemptionRepository.saveAndFlush(
                        new CouponRedemption(UUID.randomUUID(), code.getId(), userId, orderId, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Scenario: used_count atomic 控總量 — the atomic increment succeeds while used_count is below
     * total_usage_limit and is rejected (0 rows) at the limit, never exceeding it.
     */
    @Test
    void atomicUsedCountIncrementNeverExceedsTotalUsageLimit() {
        UUID campaignId = newCampaignId();
        CouponCampaign cc = couponCampaignRepository.saveAndFlush(
                new CouponCampaign(UUID.randomUUID(), campaignId, CodeType.SHARED_CODE, "LIMIT2", 2, 5));
        UUID id = cc.getId();

        assertThat(couponCampaignRepository.tryIncrementUsedCount(id)).isEqualTo(1);
        assertThat(couponCampaignRepository.tryIncrementUsedCount(id)).isEqualTo(1);
        // Limit reached: third attempt affects 0 rows and used_count stays at the limit.
        assertThat(couponCampaignRepository.tryIncrementUsedCount(id)).isZero();

        CouponCampaign reread = couponCampaignRepository.findById(id).orElseThrow();
        assertThat(reread.getUsedCount()).isEqualTo(2);
    }
}
