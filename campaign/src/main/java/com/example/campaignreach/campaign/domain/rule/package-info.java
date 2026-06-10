/**
 * Campaign module — offer rule configuration ("規則設定", task 3.2).
 *
 * <p>Holds the strongly-typed {@code RuleConfig} DTOs (one per {@code CampaignType}), their
 * schema validation ({@code RuleConfigValidator}), the application-layer {@code RuleConfigUpcaster}
 * for backward-compatible reads, and the {@code RuleConfigMapper} that (de)serializes to the
 * {@code rule_config} JSONB. The create/update API (task 4.1) calls this layer. This package MUST
 * NOT be imported by the reach module (enforced by ArchUnit).
 */
package com.example.campaignreach.campaign.domain.rule;
