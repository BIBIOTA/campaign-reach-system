package com.example.campaignreach.campaign.domain.rule;

import java.util.List;

/**
 * Raised when a {@link RuleConfig} (or its owning campaign period) fails schema validation
 * (FR-005). Carries the human-readable reason(s) so create/update (task 4.1) can reject the request
 * and report exactly why.
 */
public class RuleConfigValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> reasons;

    public RuleConfigValidationException(List<String> reasons) {
        super(String.join("; ", reasons));
        this.reasons = List.copyOf(reasons);
    }

    /** The individual validation failure reasons, each a clear message (FR-005). */
    public List<String> getReasons() {
        return reasons;
    }
}
