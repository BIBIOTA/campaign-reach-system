package com.example.campaignreach.campaign.domain.coupon;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence port for the {@link CouponCode} entity (task 3.3). */
public interface CouponCodeRepository extends JpaRepository<CouponCode, UUID> {}
