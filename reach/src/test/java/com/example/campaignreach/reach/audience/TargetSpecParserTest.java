package com.example.campaignreach.reach.audience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Fast unit tests for {@link TargetSpecParser} (task 7.2; spec §4 requirement "受眾解析與分頁展開為
 * ReachTask", scenario "受眾一律由 reach 解析"). Validates the reach trust-boundary parse of the
 * event-carried {@code targetSpec} JSON: well-formed STATIC_LIST / CONDITION shapes parse, and
 * malformed / blank / shape-violating inputs are rejected with a clear error.
 */
class TargetSpecParserTest {

    private final TargetSpecParser parser = new TargetSpecParser(new ObjectMapper());

    @Nested
    @DisplayName("合法 targetSpec 解析")
    class ValidSpecs {

        @Test
        @DisplayName("STATIC_LIST：解析出 kind 與 listId")
        void parsesStaticListWithListId() {
            UUID listId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            String json = "{\"kind\":\"STATIC_LIST\",\"listId\":\"" + listId + "\",\"conditions\":{}}";

            TargetSpec spec = parser.parse(json);

            assertThat(spec.kind()).isEqualTo(TargetSpec.Kind.STATIC_LIST);
            assertThat(spec.listId()).isEqualTo(listId);
            assertThat(spec.conditions()).isEmpty();
        }

        @Test
        @DisplayName("CONDITION：解析出 kind 與 conditions（會員等級/地區），listId 為 null")
        void parsesConditionWithConditionsMap() {
            String json =
                    "{\"kind\":\"CONDITION\",\"listId\":null,\"conditions\":{\"memberLevel\":\"GOLD\",\"region\":\"TW\"}}";

            TargetSpec spec = parser.parse(json);

            assertThat(spec.kind()).isEqualTo(TargetSpec.Kind.CONDITION);
            assertThat(spec.listId()).isNull();
            assertThat(spec.conditions()).containsEntry("memberLevel", "GOLD").containsEntry("region", "TW");
        }
    }

    @Nested
    @DisplayName("非法 targetSpec 於信任邊界被拒")
    class InvalidSpecs {

        @Test
        @DisplayName("空字串：拒絕")
        void rejectsBlank() {
            assertThatThrownBy(() -> parser.parse("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetSpec");
        }

        @Test
        @DisplayName("格式錯誤的 JSON：拒絕")
        void rejectsMalformedJson() {
            assertThatThrownBy(() -> parser.parse("{not json"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        @DisplayName("未知 kind：拒絕")
        void rejectsUnknownKind() {
            assertThatThrownBy(() -> parser.parse("{\"kind\":\"TAG_QUERY\"}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown targetSpec.kind");
        }

        @Test
        @DisplayName("blank kind：拒絕")
        void rejectsBlankKind() {
            assertThatThrownBy(() -> parser.parse("{\"kind\":\"\"}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("kind must not be blank");
        }

        @Test
        @DisplayName("STATIC_LIST 缺 listId：拒絕")
        void rejectsStaticListWithoutListId() {
            assertThatThrownBy(() -> parser.parse("{\"kind\":\"STATIC_LIST\"}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("STATIC_LIST");
        }

        @Test
        @DisplayName("CONDITION 缺 conditions：拒絕")
        void rejectsConditionWithoutConditions() {
            assertThatThrownBy(() -> parser.parse("{\"kind\":\"CONDITION\",\"conditions\":{}}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CONDITION");
        }
    }
}
