package com.example.campaignreach.shared.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Fail-fast guard for required infrastructure connectivity (design.md §3, §9).
 *
 * <p>When a mandatory connectivity property is missing — the Kafka
 * bootstrap-servers or the PostgreSQL JDBC URL — the application MUST fail to
 * start with an explicit error rather than silently degrading. Spring Boot does
 * surface a low-level failure later (e.g. a DataSource bean creation error), but
 * this validator runs first and reports a single, clear, actionable message
 * naming every missing property.
 *
 * <p>The Email provider secret is validated separately, declaratively, on
 * {@link EmailProviderProperties} via {@code @Validated}/{@code @NotBlank}, which
 * raises a {@link BindValidationException} when absent.
 */
public class RequiredInfrastructurePropertiesValidator {

    /** Kafka broker connection string — without it the Kafka client cannot connect. */
    static final String KAFKA_BOOTSTRAP_SERVERS = "spring.kafka.bootstrap-servers";

    /** PostgreSQL JDBC URL — without it no DataSource can be established. */
    static final String DATASOURCE_URL = "spring.datasource.url";

    private final Environment environment;

    public RequiredInfrastructurePropertiesValidator(Environment environment) {
        this.environment = environment;
    }

    /**
     * Validates that all required connectivity properties are present and
     * non-blank, throwing a clear {@link MissingInfrastructureConfigException}
     * listing every missing property otherwise.
     */
    public void validate() {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(environment.getProperty(KAFKA_BOOTSTRAP_SERVERS))) {
            missing.add(KAFKA_BOOTSTRAP_SERVERS);
        }
        if (!StringUtils.hasText(environment.getProperty(DATASOURCE_URL))) {
            missing.add(DATASOURCE_URL);
        }
        if (!missing.isEmpty()) {
            throw new MissingInfrastructureConfigException(missing);
        }
    }
}
