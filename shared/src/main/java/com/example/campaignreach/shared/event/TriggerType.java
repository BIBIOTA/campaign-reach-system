package com.example.campaignreach.shared.event;

/**
 * Source that caused a {@link ReachRequested} to be emitted (ER {@code trigger_type} enum).
 *
 * <ul>
 *   <li>{@link #SCHEDULED_BATCH} — scheduler scanned a RUNNING campaign and reached a send cycle;
 *       {@code sendCycle = "sched:{campaignId}:{cycleStart}"}.
 *   <li>{@link #EVENT} — a behavior event (e.g. CartAbandoned) matched a RUNNING campaign;
 *       {@code sendCycle = "event:{triggerEventId}"}.
 * </ul>
 */
public enum TriggerType {
    SCHEDULED_BATCH,
    EVENT
}
