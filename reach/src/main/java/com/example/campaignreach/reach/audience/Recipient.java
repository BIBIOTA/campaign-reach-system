package com.example.campaignreach.reach.audience;

import java.util.UUID;

/**
 * A resolved audience member (class-model {@code Recipient}; spec §4, FR-007/FR-013).
 *
 * <p>PII-minimized by design (NFR-005 / spec §10): a recipient carries only the upstream
 * {@code userId}. The actual email is <strong>not</strong> resolved here — it is looked up at send
 * time inside the dispatcher (section 9) and never persisted on the audience path. This keeps the
 * reach module free of recipient contact data until the moment of delivery.
 *
 * @param userId the upstream e-commerce member identity
 */
public record Recipient(UUID userId) {

    /** Validates the recipient invariant: {@code userId} must be present. */
    public Recipient {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
    }
}
