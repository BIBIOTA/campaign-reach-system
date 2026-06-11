package com.example.campaignreach.reach.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReachPlanTemplateExtractor} (task 9.1): reads {@code templateRef} from the
 * frozen reach-plan JSON the dispatcher joins at claim time, rejecting malformed / missing input.
 */
class ReachPlanTemplateExtractorTest {

    private final ReachPlanTemplateExtractor extractor = new ReachPlanTemplateExtractor(new ObjectMapper());

    @Test
    @DisplayName("從凍結的 reach_plan JSON 取出 templateRef")
    void extractsTemplateRef() {
        String json = "{\"channel\":\"EMAIL\",\"templateRef\":\"welcome\",\"timing\":\"SCHEDULED\"}";
        assertThat(extractor.extract(json)).isEqualTo("welcome");
    }

    @Test
    @DisplayName("空白 / null JSON 被拒")
    void rejectsBlankJson() {
        assertThatThrownBy(() -> extractor.extract("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> extractor.extract(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("格式錯誤 / 非物件 JSON 被拒")
    void rejectsMalformedOrNonObjectJson() {
        assertThatThrownBy(() -> extractor.extract("{not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> extractor.extract("[1,2,3]")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("缺少 / 空白 templateRef 被拒")
    void rejectsMissingTemplateRef() {
        assertThatThrownBy(() -> extractor.extract("{\"channel\":\"EMAIL\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> extractor.extract("{\"templateRef\":\"  \"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
