package com.example.campaignreach.campaign.domain.rule;

/**
 * Spend threshold gating an offer (FR-003).
 *
 * <ul>
 *   <li>{@link #NONE} — 無門檻: the offer applies with no minimum spend.
 *   <li>{@link #MIN_SPEND} — 滿指定金額可用: the offer applies only once the order subtotal reaches the
 *       configured {@code minSpend}.
 * </ul>
 */
public enum ThresholdMode {
    NONE,
    MIN_SPEND
}
