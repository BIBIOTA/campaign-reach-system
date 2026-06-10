package com.example.campaignreach.campaign.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the operator id attributed to the current write for audit columns
 * ({@code created_by} / {@code updated_by}).
 *
 * <p>Section 3 has no authentication yet, so the operator is supplied explicitly
 * per request (later wired from the security context). The value is held in a
 * thread-local so JPA auditing can read it during a {@code save} without threading
 * it through every method signature.
 */
public final class CurrentOperator {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private CurrentOperator() {}

    public static void set(UUID operatorId) {
        CURRENT.set(operatorId);
    }

    public static Optional<UUID> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
