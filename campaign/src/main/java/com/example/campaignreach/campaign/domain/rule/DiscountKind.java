package com.example.campaignreach.campaign.domain.rule;

/**
 * How a {@link DiscountRuleConfig} discount is expressed.
 *
 * <ul>
 *   <li>{@link #AMOUNT} — a fixed currency amount off ({@code amount}).
 *   <li>{@link #PERCENTAGE} — a percentage off ({@code percentage}, 0–100).
 * </ul>
 */
public enum DiscountKind {
    AMOUNT,
    PERCENTAGE
}
