package com.example.campaignreach.reach.audience;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * MVP seam to the upstream member system for condition-based segmentation (design.md §4, FR-007).
 *
 * <p>Mirrors the accepted-MVP-stub approach of {@code FlashSalePromotionEvaluator} (task 5.1): it
 * keeps the {@link MemberDirectory} port and its wiring in place so {@link TargetSpec.Kind#CONDITION}
 * resolution has a real collaborator, without inventing a local members/users table (member master
 * data is owned upstream — the ER has no such entity).
 *
 * <p><strong>MVP behaviour.</strong> Returns an empty recipient list rather than throwing, so a
 * condition-based campaign degrades to "no recipients yet" instead of erroring. Real member-attribute
 * resolution (member level / region) connects to the upstream e-commerce member service later; that
 * replacement is a new {@link MemberDirectory} implementation and does not touch {@link
 * AudienceResolver} or any other strategy (OCP).
 */
@Component
public class MvpMemberDirectory implements MemberDirectory {

    @Override
    public List<Recipient> findByConditions(Map<String, Object> conditions) {
        // MVP stub: the upstream member service is not yet wired. No local member table exists by
        // design, so condition-based segmentation resolves to an empty list until the real adapter
        // (member level / region lookup) is connected.
        return List.of();
    }
}
