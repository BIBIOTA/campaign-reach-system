package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.CampaignType;
import com.example.campaignreach.campaign.domain.rule.RuleConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Create-campaign request for {@code POST /internal/campaigns} (task 4.1, FR-001/FR-006).
 *
 * <p>Carries the offer-rule settings ({@link #ruleConfig}, validated per type via {@code
 * RuleConfigMapper} before persist) and the reach/target settings ({@link #targetSpec} / {@link
 * #reachPlan}). The created campaign always starts in {@code DRAFT}; the {@code status} is never
 * accepted from the client (lifecycle transitions are task 4.2).
 *
 * @param name campaign name (required, non-blank)
 * @param type campaign offer type; drives which {@link RuleConfig} subtype is expected
 * @param startAt active-period start
 * @param endAt active-period end
 * @param ruleConfig the per-type offer rule (polymorphic on {@code ruleType})
 * @param targetSpec audience targeting settings
 * @param reachPlan reach delivery settings
 */
@Schema(
        description = "建立活動請求。範例為一個可直接送出的 DISCOUNT 百分比折扣活動（滿千 9 折、發 EMAIL 給 VIP）。",
        example =
                """
                {
                  "name": "夏季全館 9 折",
                  "type": "DISCOUNT",
                  "startAt": "2026-07-01T00:00:00Z",
                  "endAt": "2026-07-31T23:59:59Z",
                  "ruleConfig": {
                    "ruleType": "DISCOUNT",
                    "schema_version": 2,
                    "kind": "PERCENTAGE",
                    "percentage": 10,
                    "thresholdMode": "MIN_SPEND",
                    "minSpend": 1000
                  },
                  "targetSpec": {
                    "kind": "CONDITION",
                    "conditions": { "memberTier": "VIP" }
                  },
                  "reachPlan": {
                    "channel": "EMAIL",
                    "templateRef": "summer-sale-2026",
                    "timing": "SCHEDULED"
                  }
                }
                """)
public record CreateCampaignRequest(
        @Schema(description = "活動名稱（必填，不可空白）", example = "夏季全館 9 折") @NotBlank(message = "name must not be blank")
                String name,
        @Schema(description = "活動優惠類型，決定預期的 ruleConfig 子型別") @NotNull(message = "type must not be null")
                CampaignType type,
        @Schema(description = "活動有效期起始時間（UTC Instant）") @NotNull(message = "startAt must not be null") Instant startAt,
        @Schema(description = "活動有效期結束時間（UTC Instant）") @NotNull(message = "endAt must not be null") Instant endAt,
        @Schema(description = "依活動類型而定的優惠規則設定（多型，依 ruleType 區分）") @NotNull(message = "ruleConfig must not be null")
                RuleConfig ruleConfig,
        @Schema(description = "觸達對象（受眾）設定") @NotNull(message = "targetSpec must not be null") @Valid
                TargetSpecDto targetSpec,
        @Schema(description = "觸達發送計畫設定") @NotNull(message = "reachPlan must not be null") @Valid
                ReachPlanDto reachPlan) {}
