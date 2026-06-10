package com.example.campaignreach.campaign.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Back-office operator accounts for the internal REST endpoints (task 4.1, §10).
 *
 * <p>Credentials and the per-username operator {@link Operator#id() UUID} come entirely from
 * configuration (bound from environment, never hardcoded secrets in code). The id is
 * <strong>deterministic</strong> per username so audit columns ({@code created_by}/{@code
 * updated_by}) are stable across requests. This config-driven in-memory operator is the MVP; a real
 * IdP/SSO is explicitly out of scope.
 *
 * @param operators configured back-office operator accounts (at least one required)
 */
@ConfigurationProperties(prefix = "campaignreach.security")
public record OperatorProperties(@NotEmpty @Valid List<Operator> operators) {

    /** Defensively copies {@code operators} to an immutable list so the bound config cannot be mutated. */
    public OperatorProperties {
        operators = operators == null ? List.of() : List.copyOf(operators);
    }

    /**
     * One operator account.
     *
     * @param username login username (HTTP Basic)
     * @param password login password (supplied from env/vault, not committed)
     * @param id deterministic operator UUID attributed to audit columns
     */
    public record Operator(@NotBlank String username, @NotBlank String password, @NotNull UUID id) {}
}
