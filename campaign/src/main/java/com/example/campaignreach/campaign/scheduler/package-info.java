/**
 * Campaign module — scheduler layer (time-based campaign triggers).
 *
 * <p>Hosts the time-driven campaign lifecycle scheduler ({@code CampaignLifecycleScheduler}) that
 * auto-advances campaigns to {@code RUNNING}/{@code ENDED} at their start/end times (task 6.1), the
 * scheduled reach-scan ({@code CampaignReachScanScheduler}) that emits activity-level
 * {@code ReachRequested} for due RUNNING campaigns (task 6.2), the module-owned scheduling enablement
 * ({@code CampaignSchedulingConfig}), and the distributed-lock wiring
 * ({@code CampaignSchedulerLockConfig}) that dedups the sweep across instances / restarts.
 */
package com.example.campaignreach.campaign.scheduler;
