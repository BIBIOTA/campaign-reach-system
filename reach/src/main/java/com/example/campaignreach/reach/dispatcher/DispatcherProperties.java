package com.example.campaignreach.reach.dispatcher;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable tunables for the two-phase {@code ReachTask} dispatcher (task 9.1), bound from
 * {@code campaignreach.reach.dispatcher.*}.
 *
 * @param batchSize maximum number of tasks claimed per poll (stage-one {@code FOR UPDATE SKIP
 *     LOCKED} batch size; default 100)
 * @param leaseDuration the claim lease written to {@code locked_until} when a task is marked
 *     PROCESSING — the window within which this worker is expected to finish the send and write back
 *     before a Reaper (task 9.3) could reclaim it (default 5m, per design.md §5/§6)
 */
@ConfigurationProperties(prefix = "campaignreach.reach.dispatcher")
public record DispatcherProperties(Integer batchSize, Duration leaseDuration) {

    /** Applies defaults so the dispatcher is usable without explicit configuration. */
    public DispatcherProperties {
        int resolvedBatchSize = batchSize == null ? 100 : batchSize;
        if (resolvedBatchSize <= 0) {
            throw new IllegalArgumentException("campaignreach.reach.dispatcher.batch-size must be positive");
        }
        batchSize = resolvedBatchSize;
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(5) : leaseDuration;
        if (leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("campaignreach.reach.dispatcher.lease-duration must be positive");
        }
    }
}
