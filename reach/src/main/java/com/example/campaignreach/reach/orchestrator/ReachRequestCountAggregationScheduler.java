package com.example.campaignreach.reach.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background tick that keeps {@code reach_request} report counters near-real-time without making the
 * dispatcher update the batch row on every task transition (task 10.2, NFR-002).
 */
@Component
public class ReachRequestCountAggregationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ReachRequestCountAggregationScheduler.class);

    private final ReachRequestCountAggregator aggregator;

    /**
     * @param aggregator set-based projector from {@code reach_task} statuses to {@code reach_request}
     *     counts
     */
    public ReachRequestCountAggregationScheduler(ReachRequestCountAggregator aggregator) {
        this.aggregator = aggregator;
    }

    /**
     * One aggregation tick. The default delay is deliberately short enough for seconds-level reporting
     * freshness while still batching many task status changes into one set-based update.
     */
    @Scheduled(fixedDelayString = "${campaignreach.reach.metrics.fixed-delay-ms:5000}")
    public void aggregateCounts() {
        int updated = aggregator.aggregateCounts();
        if (updated > 0) {
            LOG.info("Aggregated reach_request counters for {} batch(es)", updated);
        }
    }
}
