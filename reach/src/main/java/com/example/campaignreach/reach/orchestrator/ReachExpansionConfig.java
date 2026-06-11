package com.example.campaignreach.reach.orchestrator;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Always-on wiring for the reach fan-out (task 7.3): registers {@link ExpansionProperties} so the
 * {@link PagedAudienceExpander} can be tuned via {@code campaignreach.reach.*}.
 *
 * <p>Kept separate from {@link ReachOrchestratorKafkaConfig} on purpose: that config is gated behind
 * {@code campaignreach.kafka.at-least-once.enabled}, but the expander and its page-size /
 * frequency-cap tunables must exist whenever the orchestrator runs, independent of Kafka.
 */
@Configuration
@EnableConfigurationProperties(ExpansionProperties.class)
public class ReachExpansionConfig {}
