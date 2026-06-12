package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.CampaignStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Status-transition request for {@code POST /internal/campaigns/{id}/status} (task 4.2, FR-011).
 *
 * <p>Drives an operator-initiated lifecycle transition. Only the legal edges
 * (DRAFT→SCHEDULED→RUNNING→PAUSED/ENDED, PAUSED→RUNNING) are accepted; any other request is rejected
 * with HTTP 422. {@code version} is the required optimistic-lock guard (the value from a prior read):
 * a stale value fails with HTTP 409 instead of overwriting a concurrent edit (FR-001), mirroring
 * {@link UpdateCampaignRequest}.
 *
 * @param targetStatus the requested next lifecycle status
 * @param version the campaign version the client read; a stale value fails the transition with 409
 */
@Schema(
        description = "活動狀態轉換請求。範例將剛建立的活動由 DRAFT 推進到 SCHEDULED；version 需帶上一次讀到的版本。",
        example =
                """
                {
                  "targetStatus": "SCHEDULED",
                  "version": 0
                }
                """)
public record ChangeCampaignStatusRequest(
        @Schema(description = "請求轉換到的下一個生命週期狀態") @NotNull(message = "targetStatus must not be null")
                CampaignStatus targetStatus,
        @Schema(description = "客戶端先前讀到的活動版本（樂觀鎖）；值過期時轉換以 409 失敗") @NotNull(message = "version must not be null")
                Integer version) {}
