package com.example.campaignreach.campaign.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal (non-public) REST for marketing campaign CRUD (task 4.1, FR-001/FR-006/FR-007/FR-010).
 *
 * <p>All endpoints require an authenticated back-office {@code OPERATOR} (enforced by {@link
 * CampaignSecurityConfig}); the authenticated operator id is attributed to the audit columns. The
 * {@code DRAFT}→{@code SCHEDULED} confirmation that arms a campaign for reach is task 4.2 — this
 * controller never changes status.
 */
@RestController
@RequestMapping("/internal/campaigns")
@Tag(name = "活動管理", description = "行銷活動的建立、查詢、修改與生命週期狀態轉換（後台 OPERATOR 專用）")
public class CampaignController {

    private final CampaignApplicationService service;

    public CampaignController(CampaignApplicationService service) {
        this.service = service;
    }

    /** Creates a campaign in {@code DRAFT}; returns 201 with the new id (FR-001/FR-006). */
    @Operation(summary = "建立活動", description = "以合法欄位建立行銷活動，活動一律落為 DRAFT 草稿狀態；回傳新活動 id 供後續操作。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "建立成功，回傳新活動 id 與初始版本"),
        @ApiResponse(responseCode = "400", description = "請求欄位驗證失敗（如名稱空白、規則設定不合法）"),
        @ApiResponse(responseCode = "401", description = "未通過後台 OPERATOR 認證")
    })
    @PostMapping
    public ResponseEntity<CreateCampaignResponse> create(@Valid @RequestBody CreateCampaignRequest request) {
        CreateCampaignResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/internal/campaigns/" + response.id()))
                .body(response);
    }

    /** Reads one campaign; 404 if absent. */
    @Operation(summary = "查詢單一活動", description = "依活動 id 讀取活動完整內容；活動不存在時回傳 404。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查詢成功，回傳活動內容"),
        @ApiResponse(responseCode = "404", description = "查無此活動"),
        @ApiResponse(responseCode = "401", description = "未通過後台 OPERATOR 認證")
    })
    @GetMapping("/{id}")
    public CampaignView get(@Parameter(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID id) {
        return service.get(id);
    }

    /**
     * Modifies offer-rule and/or reach/target settings independently; 409 on stale version. Uses
     * {@code PATCH} rather than {@code PUT} because each omitted ({@code null}) field is left unchanged
     * — partial-update (PATCH) semantics, not whole-resource replacement.
     */
    @Operation(summary = "修改活動設定", description = "以 PATCH 部分更新優惠規則與/或觸達發送設定；省略（null）的欄位維持不變。版本過期時回傳 409。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "修改成功，回傳更新後的活動內容"),
        @ApiResponse(responseCode = "400", description = "請求欄位驗證失敗"),
        @ApiResponse(responseCode = "404", description = "查無此活動"),
        @ApiResponse(responseCode = "409", description = "樂觀鎖版本過期（與其他並行修改衝突）"),
        @ApiResponse(responseCode = "401", description = "未通過後台 OPERATOR 認證")
    })
    @PatchMapping("/{id}")
    public CampaignView update(
            @Parameter(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID id,
            @Valid @RequestBody UpdateCampaignRequest request) {
        return service.update(id, request);
    }

    /**
     * Performs an operator-driven lifecycle transition (task 4.2, FR-011): 422 on an illegal edge,
     * 409 on a stale version.
     */
    @Operation(summary = "活動狀態轉換", description = "由 OPERATOR 觸發生命週期狀態轉換（如 DRAFT→SCHEDULED）。不合法的轉換邊回傳 422，版本過期回傳 409。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "轉換成功，回傳更新後的活動內容"),
        @ApiResponse(responseCode = "404", description = "查無此活動"),
        @ApiResponse(responseCode = "409", description = "樂觀鎖版本過期（與其他並行修改衝突）"),
        @ApiResponse(responseCode = "422", description = "不合法的狀態轉換邊"),
        @ApiResponse(responseCode = "401", description = "未通過後台 OPERATOR 認證")
    })
    @PostMapping("/{id}/status")
    public CampaignView changeStatus(
            @Parameter(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID id,
            @Valid @RequestBody ChangeCampaignStatusRequest request) {
        return service.transition(id, request);
    }
}
