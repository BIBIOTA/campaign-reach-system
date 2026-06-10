package com.example.campaignreach.campaign.domain.rule;

/**
 * Raised when a <b>persisted</b> {@code rule_config} JSONB value cannot be parsed back into a {@link
 * RuleConfig} — i.e. the stored data is corrupt, malformed, or no longer upcastable.
 *
 * <p>This is a server-side data-integrity failure, NOT a client input error: {@link
 * RuleConfigMapper#fromJson} only ever reads the database column, never request bodies. It therefore
 * extends {@link IllegalStateException} so it surfaces as HTTP 500 (mirroring how {@code
 * CampaignApplicationService.fromJson} treats corrupt {@code target_spec}/{@code reach_plan}),
 * instead of being misreported as a 400 validation failure like {@link
 * RuleConfigValidationException} (which guards client-supplied rules on write).
 */
public class RuleConfigPersistenceException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public RuleConfigPersistenceException(String message) {
        super(message);
    }

    public RuleConfigPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
