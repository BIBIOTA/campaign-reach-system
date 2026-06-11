/**
 * Read-only reach-metrics query slice (task 10.3, FR-018, US-007, NFR-005).
 *
 * <p>Exposes two internal back-office queries over the reach tables maintained elsewhere:
 *
 * <ul>
 *   <li><strong>Campaign-level summary</strong> — delivered rate, failed rate, and the per-{@code
 *       reach_task_status} recipient distribution across all of a campaign's batches.
 *   <li><strong>Per-recipient lookup</strong> — a single user's reach status(es) for one campaign.
 * </ul>
 *
 * <p>This slice is strictly read-only: it never writes task state and never touches the task
 * 10.2 {@code ReachRequestCountAggregator}. It derives every figure from {@code reach_task} (grouped
 * by status), not from the {@code reach_request} counter columns. Per NFR-005 the per-recipient response is
 * PII-minimized — it returns only identifiers and status (campaign id, user id, channel, send-cycle
 * key, status), never email addresses or message content.
 *
 * <p>The package lives in the reach module and depends only on raw ids / {@code shared} contracts —
 * it never imports the campaign domain (ArchUnit {@code ModuleBoundaryTest} enforces {@code reach ↛
 * campaign}); {@code campaign_id} is treated purely as a UUID column on the reach tables.
 */
package com.example.campaignreach.reach.metrics;
