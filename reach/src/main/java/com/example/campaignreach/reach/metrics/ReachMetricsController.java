package com.example.campaignreach.reach.metrics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal (non-public) read-only REST for reach成效查詢 (task 10.3, FR-018, US-007, NFR-005).
 *
 * <p>Two GET endpoints, both under {@code /internal/reach/...}:
 *
 * <ul>
 *   <li>{@code GET /internal/reach/campaigns/{campaignId}/metrics} — 活動維度彙總 (delivered rate /
 *       failed rate / per-status distribution).
 *   <li>{@code GET /internal/reach/campaigns/{campaignId}/recipients/{userId}} — 單筆收件人狀態 (a
 *       recipient's reach status(es) for the campaign).
 * </ul>
 *
 * <p>These are secured by the same {@code /internal/**} HTTP-Basic {@code OPERATOR} filter chain
 * that the campaign module wires (see {@code CampaignSecurityConfig}, which uses {@code
 * securityMatcher("/internal/**")} — so it already covers {@code /internal/reach/**}). No second
 * {@code SecurityFilterChain} is declared here; adding one would risk ambiguous matching across
 * modules. The reach module reaches the campaign id only as a raw UUID column — it never imports the
 * campaign domain (ArchUnit {@code ModuleBoundaryTest} keeps {@code reach ↛ campaign}).
 */
@RestController
@RequestMapping("/internal/reach/campaigns")
@Tag(name = "觸達成效", description = "活動觸達成效彙總與單筆收件人狀態查詢（後台 OPERATOR 專用，唯讀）")
public class ReachMetricsController {

    private final ReachMetricsQueryService service;

    public ReachMetricsController(ReachMetricsQueryService service) {
        this.service = service;
    }

    /** 活動維度彙總: returns delivered/failed rate and the per-status recipient distribution. */
    @Operation(summary = "活動維度成效彙總", description = "回傳指定活動的送達率、失敗率與各狀態的收件人分佈；無任何收件人時各比率為 0.0（不會除以零）。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查詢成功，回傳活動維度彙總"),
        @ApiResponse(responseCode = "401", description = "未通過後台 OPERATOR 認證")
    })
    @GetMapping("/{campaignId}/metrics")
    public CampaignReachMetrics campaignMetrics(
            @Parameter(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID campaignId) {
        return service.campaignMetrics(campaignId);
    }

    /** 單筆收件人狀態: returns the recipient's reach status(es) for the campaign (PII-minimized). */
    @Operation(summary = "單筆收件人觸達狀態", description = "回傳指定活動下單一收件人的觸達狀態（已最小化個資）。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查詢成功，回傳收件人觸達狀態"),
        @ApiResponse(responseCode = "401", description = "未通過後台 OPERATOR 認證")
    })
    @GetMapping("/{campaignId}/recipients/{userId}")
    public RecipientReachView recipientStatus(
            @Parameter(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID campaignId,
            @Parameter(example = "7c9e6679-7425-40de-944b-e07fc1f90ae7") @PathVariable UUID userId) {
        return service.recipientStatus(campaignId, userId);
    }
}
