package com.example.campaignreach.campaign.domain;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing for the campaign module so the {@link Campaign}
 * aggregate's audit columns are populated on write (FR-001).
 *
 * <p>{@code @CreatedDate} / {@code @LastModifiedDate} are filled from the JVM clock;
 * {@code created_by} / {@code updated_by} are resolved from {@link CurrentOperator}.
 */
@Configuration
@EnableJpaAuditing
public class CampaignAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return CurrentOperator::get;
    }
}
