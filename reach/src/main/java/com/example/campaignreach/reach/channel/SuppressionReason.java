package com.example.campaignreach.reach.channel;

/**
 * Why a recipient is on the suppression list (ER {@code suppression_reason} enum; design.md §10).
 *
 * <p>All three reasons are <strong>non-retryable</strong>: a hit means the reach task is marked {@link
 * com.example.campaignreach.reach.orchestrator.ReachTaskStatus#FAILED} and not sent (FR-015). The
 * maintenance and source of these entries belong to the upstream e-commerce site; this system only
 * consumes the result.
 */
public enum SuppressionReason {
    /** 退訂 — the recipient unsubscribed (design.md §6 "不可重試" classification). */
    UNSUBSCRIBE,
    /** 硬退信 — a permanent delivery failure marked the address undeliverable. */
    HARD_BOUNCE,
    /** 投訴 — the recipient reported the message as spam/abuse. */
    COMPLAINT
}
