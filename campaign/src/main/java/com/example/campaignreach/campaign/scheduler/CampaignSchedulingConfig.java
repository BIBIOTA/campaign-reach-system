package com.example.campaignreach.campaign.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling for the campaign module so the module owns its own scheduling
 * enablement (mirroring how {@code CampaignAuditingConfig} / {@code CampaignSecurityConfig} own
 * their concerns), rather than relying on the {@code :app} bootstrap.
 *
 * <p>Activates the {@code @Scheduled} sweep in {@link CampaignLifecycleScheduler} (task 6.1).
 */
@Configuration
@EnableScheduling
public class CampaignSchedulingConfig {}
