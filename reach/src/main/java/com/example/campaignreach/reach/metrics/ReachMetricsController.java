package com.example.campaignreach.reach.metrics;

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
public class ReachMetricsController {

    private final ReachMetricsQueryService service;

    public ReachMetricsController(ReachMetricsQueryService service) {
        this.service = service;
    }

    /** 活動維度彙總: returns delivered/failed rate and the per-status recipient distribution. */
    @GetMapping("/{campaignId}/metrics")
    public CampaignReachMetrics campaignMetrics(@PathVariable UUID campaignId) {
        return service.campaignMetrics(campaignId);
    }

    /** 單筆收件人狀態: returns the recipient's reach status(es) for the campaign (PII-minimized). */
    @GetMapping("/{campaignId}/recipients/{userId}")
    public RecipientReachView recipientStatus(@PathVariable UUID campaignId, @PathVariable UUID userId) {
        return service.recipientStatus(campaignId, userId);
    }
}
