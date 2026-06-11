package com.example.campaignreach.reach.audience;

import java.util.List;
import java.util.Map;

/**
 * Port to the upstream e-commerce member system for condition-based segmentation (design.md §4,
 * FR-007/FR-013).
 *
 * <p>Member master data (member level, region, …) is owned <strong>upstream</strong> by the
 * e-commerce main site — this system does not own a members/users table (the ER has no such entity).
 * {@link TargetSpec.Kind#CONDITION} resolution therefore delegates across this port instead of
 * querying a local table, keeping reach free of member master data.
 */
public interface MemberDirectory {

    /**
     * Finds the members matching simple segmentation conditions (e.g. {@code memberLevel}, {@code
     * region}).
     *
     * @param conditions the segmentation conditions from the {@code CONDITION} targetSpec
     * @return matching recipients (never {@code null})
     */
    List<Recipient> findByConditions(Map<String, Object> conditions);
}
