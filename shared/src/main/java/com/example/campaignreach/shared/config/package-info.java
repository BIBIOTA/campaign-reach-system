/**
 * Shared kernel — base cross-module configuration and common contracts.
 *
 * <p>Holds only stable cross-module contracts; MUST NOT hold campaign/reach
 * domain entities, repositories or services. Provides base PostgreSQL /
 * Kafka connectivity config, scheduling infrastructure, the validated Email
 * provider secret binding and fail-fast validation of required connectivity
 * properties (design.md §3, §9, §10).
 */
package com.example.campaignreach.shared.config;
