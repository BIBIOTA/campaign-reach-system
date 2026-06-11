package com.example.campaignreach.reach.audience;

import java.util.List;

/**
 * Resolves an activity-level {@link TargetSpec} into a recipient list, entirely within the reach
 * module (class-model {@code AudienceResolver}; spec §4, FR-007/FR-013).
 *
 * <p><strong>Audience is always resolved by reach.</strong> The campaign module never expands
 * recipients: it only publishes the {@code targetSpec} on {@code ReachRequested}. The orchestrator
 * hands that spec here and reach decides "who" — the single seam where audience becomes a concrete
 * recipient list (diagram {@code 03-class-domain-model.puml}, note "受眾解析一律在 reach 端進行").
 */
public interface AudienceResolver {

    /**
     * Resolves a targeting spec into recipients.
     *
     * @param spec the validated activity-level targeting spec
     * @return the resolved recipients (never {@code null})
     */
    List<Recipient> resolve(TargetSpec spec);
}
