/**
 * Campaign module — reach-request publishing seam.
 *
 * <p>Hosts {@code ReachRequestPublisher}, the single Kafka producer the campaign module uses to emit
 * activity-level {@code ReachRequested} events to the {@code reach.requested} topic. Both trigger
 * paths converge here: the scheduled batch scan (task 6.2) and the behavior-event path (task 6.3).
 * The campaign module communicates with reach only through the shared event contract — never via
 * reach internals.
 */
package com.example.campaignreach.campaign.publish;
