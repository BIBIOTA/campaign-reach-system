/**
 * Campaign module — scheduler layer (time-based campaign triggers).
 *
 * <p>Hosts the time-driven campaign lifecycle scheduler ({@code CampaignLifecycleScheduler}) that
 * auto-advances campaigns to {@code RUNNING}/{@code ENDED} at their start/end times (task 6.1), and
 * the module-owned scheduling enablement ({@code CampaignSchedulingConfig}).
 */
package com.example.campaignreach.campaign.scheduler;
