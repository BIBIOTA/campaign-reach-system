package com.example.campaignreach.shared.config;

import java.util.List;

/**
 * Raised at startup when required infrastructure connectivity configuration is
 * missing (design.md §3, §9). Carries an explicit message naming every missing
 * property so the failure is actionable and never silently degraded.
 */
public class MissingInfrastructureConfigException extends RuntimeException {

    private final List<String> missingProperties;

    public MissingInfrastructureConfigException(List<String> missingProperties) {
        super("Missing required infrastructure configuration: "
                + String.join(", ", missingProperties)
                + ". The application cannot start without these; "
                + "configure them via environment variables / vault (no silent degradation).");
        this.missingProperties = List.copyOf(missingProperties);
    }

    public List<String> getMissingProperties() {
        return missingProperties;
    }
}
