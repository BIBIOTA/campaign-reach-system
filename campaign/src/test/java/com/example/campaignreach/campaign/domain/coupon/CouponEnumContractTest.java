package com.example.campaignreach.campaign.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Fast (no-DB) guard on the coupon enum contracts (spec §4 / FR-002). The full
 * round-trip-through-PostgreSQL persistence of these values lives in the Testcontainers test in
 * {@code :app} (auto-skipped without Docker).
 */
class CouponEnumContractTest {

    /** Spec §4: code_type enum is exactly the two distribution strategies. */
    @Test
    void codeTypeEnumHasExactlyTheTwoSpecValues() {
        assertThat(CodeType.values()).extracting(Enum::name).containsExactlyInAnyOrder("SHARED_CODE", "UNIQUE_CODE");
    }

    /** Spec §4: coupon_code_status enum is exactly the four lifecycle values. */
    @Test
    void couponCodeStatusEnumHasExactlyTheFourSpecValues() {
        assertThat(CouponCodeStatus.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("AVAILABLE", "ASSIGNED", "REDEEMED", "EXPIRED");
    }
}
