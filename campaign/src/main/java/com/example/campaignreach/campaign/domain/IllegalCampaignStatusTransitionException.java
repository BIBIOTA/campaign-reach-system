package com.example.campaignreach.campaign.domain;

/**
 * Raised when a campaign status transition is not one of the legal lifecycle edges (FR-011).
 *
 * <p>The legal edges are DRAFT→SCHEDULED, SCHEDULED→RUNNING, RUNNING→PAUSED, PAUSED→RUNNING,
 * RUNNING→ENDED and PAUSED→ENDED; {@code ENDED} is terminal. Any other transition (e.g. ENDED→RUNNING)
 * is rejected with this exception, whose message names the offending {@code from→to} states.
 */
public class IllegalCampaignStatusTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Builds the rejection reason naming the illegal {@code from→to} edge.
     *
     * @param from the current campaign status
     * @param to the requested target status
     */
    public IllegalCampaignStatusTransitionException(CampaignStatus from, CampaignStatus to) {
        super("illegal campaign status transition: " + from + " -> " + to);
    }
}
