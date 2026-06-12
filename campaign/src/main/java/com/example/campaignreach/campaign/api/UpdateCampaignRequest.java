package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.rule.RuleConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Modify-campaign request for {@code PUT /internal/campaigns/{id}} (task 4.1, FR-001/FR-007/FR-010).
 *
 * <p>Offer-rule settings and reach/target settings are updated <strong>independently</strong>: any
 * {@code null} field is left unchanged, so a caller can patch only {@link #ruleConfig}, only {@link
 * #reachPlan}, etc. {@code status} is intentionally NOT updatable here — lifecycle transitions are
 * task 4.2. {@code version} is the required optimistic-lock guard supplied by the client (the value
 * from a prior read): if it no longer matches the stored campaign, a concurrent edit landed first
 * and the request fails with HTTP 409 instead of silently overwriting (FR-001).
 *
 * @param version the campaign version the client read; a stale value fails the update with 409
 * @param name new name; {@code null} leaves it unchanged
 * @param startAt new period start; {@code null} leaves it unchanged
 * @param endAt new period end; {@code null} leaves it unchanged
 * @param ruleConfig new per-type offer rule; {@code null} leaves the stored rule unchanged
 * @param targetSpec new targeting settings; {@code null} leaves them unchanged
 * @param reachPlan new reach settings; {@code null} leaves them unchanged
 */
@Schema(
        description = "修改活動請求（PATCH 部分更新）。範例只改 reachPlan（改用 PUSH 推播），其餘欄位省略即維持不變；" + "version 必須帶上一次讀到的版本，過期會回 409。",
        example =
                """
                {
                  "version": 0,
                  "reachPlan": {
                    "channel": "PUSH",
                    "templateRef": "summer-sale-2026-push",
                    "timing": "SCHEDULED"
                  }
                }
                """)
public record UpdateCampaignRequest(
        @Schema(description = "客戶端先前讀到的活動版本（樂觀鎖）；值過期時更新以 409 失敗") @NotNull(message = "version must not be null")
                Integer version,
        @Schema(description = "新活動名稱；null 表示不變") String name,
        @Schema(description = "新活動有效期起始時間；null 表示不變") Instant startAt,
        @Schema(description = "新活動有效期結束時間；null 表示不變") Instant endAt,
        @Schema(description = "新的依類型優惠規則；null 表示維持原規則不變") RuleConfig ruleConfig,
        @Schema(description = "新的觸達對象設定；null 表示不變") @Valid TargetSpecDto targetSpec,
        @Schema(description = "新的觸達發送設定；null 表示不變") @Valid ReachPlanDto reachPlan) {}
