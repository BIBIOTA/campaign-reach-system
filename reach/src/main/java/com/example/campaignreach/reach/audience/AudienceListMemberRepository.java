package com.example.campaignreach.reach.audience;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence port for {@link AudienceListMember} — the static-list membership lookup backing
 * {@link TargetSpec.Kind#STATIC_LIST} resolution (spec §4, FR-007/FR-013).
 */
public interface AudienceListMemberRepository extends JpaRepository<AudienceListMember, AudienceListMemberId> {

    /**
     * Returns every membership row of the given static list.
     *
     * @param listId the {@code audience_list} id from the {@code STATIC_LIST} targetSpec
     * @return the membership rows (empty when the list has no members)
     */
    List<AudienceListMember> findByListId(UUID listId);
}
