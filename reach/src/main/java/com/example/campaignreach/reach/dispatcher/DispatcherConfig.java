package com.example.campaignreach.reach.dispatcher;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wiring for the reach dispatcher (task 9.1): binds {@link DispatcherProperties} and enables Spring
 * scheduling so the reach module owns its own scheduling enablement (mirroring {@code
 * CampaignSchedulingConfig}) rather than relying on the {@code :app} bootstrap or the campaign module.
 *
 * <p>Activates the {@code @Scheduled} poll in {@link ReachTaskDispatcher}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(DispatcherProperties.class)
public class DispatcherConfig {}
