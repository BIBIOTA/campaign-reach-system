package com.example.campaignreach.reach.dispatcher;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The Reaper background job (task 9.3, spec「可靠發送與重試」scenario「回收卡死任務」, design.md §5). It
 * periodically reclaims {@code reach_task} rows stuck in PROCESSING whose lease has expired — the
 * symptom of a worker that claimed a task ({@link ReachTaskDispatchDao#claimBatch stage one}) then
 * crashed before writing the outcome back ({@code markSent}/{@code scheduleRetry}/{@code
 * markFailed}/{@code markDeadLettered}, stage two). Each tick resets every such row to PENDING via a
 * single guarded {@link ReachTaskDispatchDao#reapExpiredLeases UPDATE}, so the dispatcher's next poll
 * re-claims it and the task is never permanently stuck (state edge {@code PROCESSING --> PENDING},
 * the lease-recovery transition implied by the lease note in
 * {@code diagrams/02-state-campaign-and-task-lifecycle.puml}).
 *
 * <p><strong>No ShedLock.</strong> Unlike {@code CampaignLifecycleScheduler} — which emits
 * side-effecting lifecycle changes and therefore needs {@code @SchedulerLock} to run once cluster-wide
 * — the reap is a single idempotent guarded UPDATE: the {@code WHERE status='PROCESSING' AND
 * locked_until < now} predicate means a row already reset to PENDING no longer matches, so two reapers
 * (or a reaper racing the dispatcher) resetting the same row is harmless. Concurrent reapers are
 * naturally safe, so no distributed lock is wired.
 *
 * <p><strong>Lease-vs-reaper race (accepted bound).</strong> A worker that is slow-but-alive could
 * have its lease reaped and the task re-dispatched concurrently, opening an at-least-once duplicate
 * send window. This is the design's accepted bound (design.md §6「降低重複發送機率，而非嚴格
 * exactly-once」); the lease duration ({@link DispatcherProperties#leaseDuration}, default 5m) is the
 * guard sizing that window. The Reaper does not attempt to eliminate it (no distributed abort).
 *
 * <p>Activated by the {@code @EnableScheduling} already declared on {@link DispatcherConfig} (shared
 * with the dispatcher poll — no duplicate enablement here).
 */
@Component
public class ReachTaskReaper {

    private static final Logger LOG = LoggerFactory.getLogger(ReachTaskReaper.class);

    private final ReachTaskDispatchDao dispatchDao;

    /**
     * @param dispatchDao the dispatcher persistence; the Reaper's reset is a sibling of its
     *     write-backs (same lease columns, same short-transaction pattern)
     */
    public ReachTaskReaper(ReachTaskDispatchDao dispatchDao) {
        this.dispatchDao = dispatchDao;
    }

    /**
     * One reap tick: reset every PROCESSING task whose lease expired before {@code now} back to
     * PENDING. A single {@link Instant#now()} is captured as the lease-expiry cutoff for the tick, and
     * the reset count is logged at INFO when any row was reclaimed.
     */
    @Scheduled(fixedDelayString = "${campaignreach.reach.reaper.fixed-delay-ms:30000}")
    public void reapExpiredLeases() {
        Instant now = Instant.now();
        int reaped = dispatchDao.reapExpiredLeases(now);
        if (reaped > 0) {
            LOG.info("Reaper reset {} expired-lease PROCESSING reach_task(s) back to PENDING", reaped);
        }
    }
}
