package com.example.campaignreach.reach.channel;

import com.example.campaignreach.shared.event.Channel;
import java.util.UUID;

/**
 * The unit of work handed to a {@link ChannelAdapter} for delivery (class-model {@code ReachMessage};
 * spec §4, FR-009).
 *
 * <p>PII-minimized by design (NFR-005 / spec §10): like {@code Recipient}, a message carries only the
 * upstream {@code userId} plus what the adapter needs to render and route — it never carries the
 * resolved email address. The adapter resolves contact data at send time and does not persist it.
 *
 * @param userId the upstream e-commerce member identity to reach
 * @param channel the delivery channel chosen by the dispatcher from the {@code reachPlan}
 * @param templateRef the content template reference to render for this send
 */
public record ReachMessage(UUID userId, Channel channel, String templateRef) {

    /** Validates the message invariants: all fields must be present. */
    public ReachMessage {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }
        if (templateRef == null || templateRef.isBlank()) {
            throw new IllegalArgumentException("templateRef must not be blank");
        }
    }
}
