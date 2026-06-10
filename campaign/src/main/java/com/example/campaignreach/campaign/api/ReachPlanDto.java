package com.example.campaignreach.campaign.api;

import com.example.campaignreach.shared.event.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Reach delivery plan request/view payload (class-model {@code ReachPlan}).
 *
 * <p>Reuses the shared kernel {@link Channel} enum (the cross-module stable contract). There is no
 * per-type validator yet (FR-010 scope), so this DTO is persisted as a raw JSON string on the {@code
 * reach_plan} column while staying strongly typed on the wire per the class diagram.
 *
 * @param channel the delivery channel (EMAIL / SMS / PUSH)
 * @param templateRef the message template reference
 * @param timing whether the reach is scheduled or event-driven
 */
public record ReachPlanDto(
        @NotNull(message = "reachPlan.channel must not be null") Channel channel,
        @NotBlank(message = "reachPlan.templateRef must not be blank") String templateRef,
        @NotNull(message = "reachPlan.timing must not be null") Timing timing) {

    /** Reach timing (class-model {@code ReachPlan.timing}). */
    public enum Timing {
        SCHEDULED,
        EVENT
    }
}
