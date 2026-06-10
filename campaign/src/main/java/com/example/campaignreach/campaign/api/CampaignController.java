package com.example.campaignreach.campaign.api;

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
public class CampaignController {

    private final CampaignApplicationService service;

    public CampaignController(CampaignApplicationService service) {
        this.service = service;
    }

    /** Creates a campaign in {@code DRAFT}; returns 201 with the new id (FR-001/FR-006). */
    @PostMapping
    public ResponseEntity<CreateCampaignResponse> create(@Valid @RequestBody CreateCampaignRequest request) {
        CreateCampaignResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/internal/campaigns/" + response.id()))
                .body(response);
    }

    /** Reads one campaign; 404 if absent. */
    @GetMapping("/{id}")
    public CampaignView get(@PathVariable UUID id) {
        return service.get(id);
    }

    /**
     * Modifies offer-rule and/or reach/target settings independently; 409 on stale version. Uses
     * {@code PATCH} rather than {@code PUT} because each omitted ({@code null}) field is left unchanged
     * — partial-update (PATCH) semantics, not whole-resource replacement.
     */
    @PatchMapping("/{id}")
    public CampaignView update(@PathVariable UUID id, @Valid @RequestBody UpdateCampaignRequest request) {
        return service.update(id, request);
    }

    /**
     * Performs an operator-driven lifecycle transition (task 4.2, FR-011): 422 on an illegal edge,
     * 409 on a stale version.
     */
    @PostMapping("/{id}/status")
    public CampaignView changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeCampaignStatusRequest request) {
        return service.transition(id, request);
    }
}
