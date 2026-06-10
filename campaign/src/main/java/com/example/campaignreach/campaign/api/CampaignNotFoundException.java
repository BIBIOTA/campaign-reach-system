package com.example.campaignreach.campaign.api;

import java.util.UUID;

/** Raised when a campaign id is not found, mapped to HTTP 404 by the controller advice (task 4.1). */
public class CampaignNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CampaignNotFoundException(UUID id) {
        super("campaign not found: " + id);
    }
}
