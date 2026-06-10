package com.example.campaignreach.campaign.api;

import org.springframework.boot.SpringBootConfiguration;

/**
 * Minimal Spring Boot configuration anchor for the campaign-module web-slice tests.
 *
 * <p>The deployable {@code @SpringBootApplication} lives in {@code :app}, so the {@code :campaign}
 * test classpath has no primary configuration for {@code @WebMvcTest} to find. This empty
 * {@code @SpringBootConfiguration} provides that anchor; {@code @WebMvcTest} supplies the web-layer
 * auto-configuration itself, so {@code @EnableAutoConfiguration} here would broaden the slice and
 * shadow the controller mappings with the full resource-handling chain.
 */
@SpringBootConfiguration
class CampaignApiTestApplication {}
