package com.example.campaignreach.reach.audience;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Default {@link AudienceResolver} that dispatches on {@link TargetSpec.Kind} (spec §4,
 * FR-007/FR-013).
 *
 * <p>Two strategies in the MVP (design.md §4):
 *
 * <ul>
 *   <li>{@link TargetSpec.Kind#STATIC_LIST} → queries {@code audience_list_member} by {@code listId}
 *       via {@link AudienceListMemberRepository}, mapping each membership row to a {@link Recipient}.
 *   <li>{@link TargetSpec.Kind#CONDITION} → delegates to the {@link MemberDirectory} port (member
 *       level / region), since member master data lives upstream.
 * </ul>
 *
 * <p>Dispatch is by kind, so adding a future kind (e.g. a tag system) means adding a branch/strategy,
 * not editing the existing ones (OCP) — mirroring the campaign evaluator-registry pattern.
 */
@Component
public class StrategyAudienceResolver implements AudienceResolver {

    private final AudienceListMemberRepository audienceListMemberRepository;
    private final MemberDirectory memberDirectory;

    public StrategyAudienceResolver(
            AudienceListMemberRepository audienceListMemberRepository, MemberDirectory memberDirectory) {
        this.audienceListMemberRepository = audienceListMemberRepository;
        this.memberDirectory = memberDirectory;
    }

    @Override
    public List<Recipient> resolve(TargetSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("targetSpec must not be null");
        }
        return switch (spec.kind()) {
            case STATIC_LIST -> resolveStaticList(spec);
            case CONDITION -> memberDirectory.findByConditions(spec.conditions());
        };
    }

    private List<Recipient> resolveStaticList(TargetSpec spec) {
        return audienceListMemberRepository.findByListId(spec.listId()).stream()
                .map(member -> new Recipient(member.getUserId()))
                .toList();
    }
}
