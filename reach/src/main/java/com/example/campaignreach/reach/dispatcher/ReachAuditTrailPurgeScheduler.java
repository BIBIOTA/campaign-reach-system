package com.example.campaignreach.reach.dispatcher;

import com.example.campaignreach.reach.dispatcher.ReachAuditTrailPurger.PurgeResult;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background tick that enforces the data-retention policy on the reach audit trail (task 11.1,
 * scenario「觸達稽核軌跡屆期歸檔或刪除」, NFR-005). Each tick captures a single {@link Instant#now()} as the
 * pass time and delegates the FK-ordered, terminal-state-guarded delete to {@link
 * ReachAuditTrailPurger}; the purged count is logged at INFO only when rows were actually removed
 * (mirroring {@link ReachTaskReaper} / {@code ReachRequestCountAggregationScheduler}).
 *
 * <p>Activated by the {@code @EnableScheduling} already declared on {@link DispatcherConfig}; the
 * default delay is deliberately coarse (hourly) because retention is a slow housekeeping sweep, not a
 * latency-sensitive path.
 */
@Component
public class ReachAuditTrailPurgeScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ReachAuditTrailPurgeScheduler.class);

    private final ReachAuditTrailPurger purger;

    /**
     * @param purger the FK-ordered, terminal-state-guarded audit-trail purge component
     */
    public ReachAuditTrailPurgeScheduler(ReachAuditTrailPurger purger) {
        this.purger = purger;
    }

    /** One retention purge tick. */
    @Scheduled(fixedDelayString = "${campaignreach.reach.retention.fixed-delay-ms:3600000}")
    public void purgeExpired() {
        PurgeResult purged = purger.purgeExpired(Instant.now());
        if (purged.tasks() > 0) {
            LOG.info(
                    "Retention purge removed {} expired reach_task(s) and {} send_result row(s)",
                    purged.tasks(),
                    purged.sendResults());
        }
    }
}
