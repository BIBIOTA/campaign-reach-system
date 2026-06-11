package com.example.campaignreach.reach.audience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Fast unit tests for {@link StrategyAudienceResolver} (task 7.2; spec §4 requirement "受眾解析與分頁展開為
 * ReachTask", scenario "受眾一律由 reach 解析"). The static-list repository and the {@link MemberDirectory}
 * port are mocked, so the test asserts the reach module's dispatch-by-kind: STATIC_LIST routes to the
 * member repository and CONDITION routes to the upstream-member port — campaign never expands
 * recipients.
 */
@ExtendWith(MockitoExtension.class)
class StrategyAudienceResolverTest {

    private static final UUID LIST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private AudienceListMemberRepository audienceListMemberRepository;

    @Mock
    private MemberDirectory memberDirectory;

    private StrategyAudienceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new StrategyAudienceResolver(audienceListMemberRepository, memberDirectory);
    }

    @Nested
    @DisplayName("受眾一律由 reach 解析")
    class ResolvesWithinReach {

        @Test
        @DisplayName("STATIC_LIST：以 listId 查 audience_list_member，映射為 Recipient")
        void staticListRoutesToMemberRepository() {
            when(audienceListMemberRepository.findByListId(LIST_ID))
                    .thenReturn(List.of(new TestMember(USER_A), new TestMember(USER_B)));
            TargetSpec spec = new TargetSpec(TargetSpec.Kind.STATIC_LIST, LIST_ID, Map.of());

            List<Recipient> recipients = resolver.resolve(spec);

            assertThat(recipients).containsExactly(new Recipient(USER_A), new Recipient(USER_B));
            verifyNoInteractions(memberDirectory);
        }

        @Test
        @DisplayName("CONDITION：以條件（會員等級/地區）委派 MemberDirectory port")
        void conditionRoutesToMemberDirectory() {
            Map<String, Object> conditions = Map.of("memberLevel", "GOLD", "region", "TW");
            when(memberDirectory.findByConditions(conditions)).thenReturn(List.of(new Recipient(USER_A)));
            TargetSpec spec = new TargetSpec(TargetSpec.Kind.CONDITION, null, conditions);

            List<Recipient> recipients = resolver.resolve(spec);

            assertThat(recipients).containsExactly(new Recipient(USER_A));
            verifyNoInteractions(audienceListMemberRepository);
        }
    }

    /** Minimal test double exposing a fixed {@code userId} (entity has no public constructor). */
    private static final class TestMember extends AudienceListMember {
        private final UUID userId;

        private TestMember(UUID userId) {
            this.userId = userId;
        }

        @Override
        public UUID getUserId() {
            return userId;
        }
    }
}
