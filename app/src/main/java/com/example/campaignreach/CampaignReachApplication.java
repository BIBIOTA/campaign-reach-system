package com.example.campaignreach;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single deployable entry point for the modular monolith.
 *
 * <p>The package root {@code com.example.campaignreach} sits above the
 * {@code campaign}, {@code reach} and {@code shared} bounded modules, so the
 * default component scan covers all three modules within one deployment unit.
 */
@SpringBootApplication
public class CampaignReachApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampaignReachApplication.class, args);
    }
}
