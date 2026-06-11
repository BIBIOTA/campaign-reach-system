package com.example.campaignreach.reach.dispatcher;

import com.example.campaignreach.reach.channel.ChannelAdapter;
import com.example.campaignreach.reach.channel.ReachMessage;
import com.example.campaignreach.reach.channel.RetryableSendException;
import com.example.campaignreach.reach.channel.SendResult;
import com.example.campaignreach.reach.channel.SuppressionGuard;
import com.example.campaignreach.reach.channel.SuppressionVerdict;
import com.example.campaignreach.shared.event.Channel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two-phase transactional {@code ReachTask} dispatcher (task 9.1, spec「可靠發送與重試」+
 * 「外部通道中斷的穩定降級」, §5/§6, NFR-002/NFR-003). Each poll claims a batch under {@code FOR UPDATE
 * SKIP LOCKED}, then for each claimed task performs the external send <em>outside</em> any DB
 * transaction and writes the outcome back in a fresh short transaction.
 *
 * <p><strong>Phase boundary.</strong> {@link ReachTaskDispatchDao#claimBatch} commits the PROCESSING
 * mark + lease and releases the DB connection before any {@link ChannelAdapter#send} runs; the
 * write-back ({@link ReachTaskDispatchDao#markSent}/{@code scheduleRetry}/{@code markFailed}) opens a
 * new transaction. So a slow provider call never holds a connection, and a crash mid-send leaves the
 * row PROCESSING with an expired lease for the Reaper (task 9.3) to reclaim — at-least-once send
 * semantics (design.md §5/§6).
 *
 * <p><strong>Breaker pre-check (scenario「breaker 開啟時不卡任務」).</strong> Before claiming, the
 * dispatcher asks each adapter {@link ChannelAdapter#isAvailable()} and only claims tasks for
 * available channels. A channel whose breaker is OPEN is excluded, so its tasks stay PENDING and are
 * re-scanned once the breaker recovers — they are never marked PROCESSING just to fast-fail.
 *
 * <p><strong>Breaker fast-fail after PROCESSING (scenario「已 PROCESSING 後 breaker 失敗」).</strong> If
 * a send throws {@link RetryableSendException} (including a breaker short-circuit with {@code
 * isBreakerOpen()}), the dispatcher treats it uniformly as a retryable failure → stage-two
 * RETRY_SCHEDULED with exponential backoff, never leaving the task stuck in PROCESSING.
 *
 * <p><strong>Per-task isolation.</strong> Mirrors {@code CampaignLifecycleScheduler}: each task's
 * send + write-back runs inside a broad catch so one task's unexpected failure does not abort the
 * batch. {@code FOR UPDATE SKIP LOCKED} already makes concurrent workers claim disjoint rows, so the
 * poll needs no {@code @SchedulerLock}.
 *
 * <p><strong>Scope (task 9.1).</strong> The retryable-vs-non-retryable provider taxonomy beyond the
 * suppression FAILED branch, and the retry-exhaustion → {@code reach.dlq} publishing, are task 9.2;
 * here retries exhaust to FAILED (the DLQ leg is a documented seam). The Reaper is task 9.3, and the
 * campaign PAUSED/ENDED cancellation re-check is task 10.1 — neither is implemented here.
 */
@Component
public class ReachTaskDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(ReachTaskDispatcher.class);

    private final ReachTaskDispatchDao dispatchDao;
    private final ChannelAdapterRegistry adapterRegistry;
    private final SuppressionGuard suppressionGuard;
    private final DispatcherProperties properties;
    private final String workerId;

    /**
     * @param dispatchDao the two-phase claim / write-back persistence
     * @param adapterRegistry routes a channel to its adapter
     * @param suppressionGuard pre-send suppression check (a hit → non-retryable FAILED)
     * @param properties batch size + lease duration tunables
     */
    public ReachTaskDispatcher(
            ReachTaskDispatchDao dispatchDao,
            ChannelAdapterRegistry adapterRegistry,
            SuppressionGuard suppressionGuard,
            DispatcherProperties properties) {
        this.dispatchDao = dispatchDao;
        this.adapterRegistry = adapterRegistry;
        this.suppressionGuard = suppressionGuard;
        this.properties = properties;
        this.workerId = resolveWorkerId();
    }

    /**
     * One dispatch poll: claim a batch of dispatchable tasks for currently-available channels and
     * dispatch each. A single {@link Instant#now()} is captured for the whole tick (claim eligibility
     * + lease basis + write-back times) for a consistent view.
     */
    @Scheduled(fixedDelayString = "${campaignreach.reach.dispatcher.fixed-delay-ms:5000}")
    public void dispatchPoll() {
        Instant now = Instant.now();
        List<Channel> eligibleChannels = eligibleChannels();
        if (eligibleChannels.isEmpty()) {
            return; // every channel is degraded (breaker OPEN) or unbound — leave tasks PENDING
        }
        List<ClaimedTask> claimed = dispatchDao.claimBatch(
                workerId, eligibleChannels, properties.batchSize(), properties.leaseDuration(), now);
        for (ClaimedTask task : claimed) {
            dispatch(task, now);
        }
    }

    /** The channels whose adapter is registered and currently available (breaker not OPEN). */
    private List<Channel> eligibleChannels() {
        List<Channel> eligible = new ArrayList<>();
        for (Channel channel : Channel.values()) {
            adapterRegistry
                    .forChannel(channel)
                    .filter(ChannelAdapter::isAvailable)
                    .ifPresent(adapter -> eligible.add(channel));
        }
        return eligible;
    }

    /**
     * Dispatches one claimed task: pre-send suppression check, then the out-of-transaction send, then
     * the stage-two write-back. The broad catch isolates one task's unexpected failure from the rest of
     * the batch; the task remains PROCESSING with its lease and is recovered by the Reaper (task 9.3).
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // deliberate broad catch: per-task exception isolation
    private void dispatch(ClaimedTask task, Instant now) {
        try {
            // Pre-send suppression (退訂 / 硬退信 / 投訴) is a non-retryable reason → FAILED, do not send.
            SuppressionVerdict verdict = suppressionGuard.evaluate(task.userId(), task.channel());
            if (verdict.suppressed()) {
                dispatchDao.markFailed(task.taskId(), "suppressed:" + verdict.reason(), now);
                return;
            }

            ChannelAdapter adapter = adapterRegistry
                    .forChannel(task.channel())
                    .orElseThrow(() -> new IllegalStateException("no ChannelAdapter for channel " + task.channel()));
            ReachMessage message = new ReachMessage(task.userId(), task.channel(), task.templateRef());
            SendResult result = adapter.send(message);
            dispatchDao.markSent(task.taskId(), result.providerMessageId(), now);
        } catch (RetryableSendException retryable) {
            // Retryable failure (transient provider error OR breaker short-circuit after PROCESSING):
            // stage-two write-back to RETRY_SCHEDULED with exponential backoff, or FAILED once exhausted.
            writeBackRetryable(task, retryable.getMessage(), now);
        } catch (RuntimeException unexpected) {
            // A genuinely-unexpected exception is INTENTIONALLY routed through the retryable path so a
            // single task's failure is isolated (per-task isolation, see class javadoc) and the task is
            // never lost: it retries with backoff and converges to FAILED once attempts are exhausted.
            // Fine-grained permanent-error fast-fail classification (so a non-retryable failure FAILs
            // immediately instead of burning retries) is deliberately DEFERRED to task 9.2 with the DLQ
            // leg; 9.1 keeps one uniform retryable fallback on purpose.
            LOG.warn(
                    "Unexpected failure dispatching reach_task {}: {}",
                    task.taskId(),
                    unexpected.getMessage(),
                    unexpected);
            writeBackRetryable(task, "unexpected:" + unexpected.getMessage(), now);
        }
    }

    /**
     * Stage-two write-back for a retryable failure: schedule the next exponential-backoff retry, or —
     * once {@link RetryBackoffSchedule#MAX_ATTEMPTS} retries are exhausted — terminate as FAILED. (Task
     * 9.2 owns the retry-exhaustion → {@code reach.dlq} leg that follows the FAILED transition.)
     */
    private void writeBackRetryable(ClaimedTask task, String error, Instant now) {
        if (RetryBackoffSchedule.canRetry(task.retryCount())) {
            Duration backoff = RetryBackoffSchedule.backoffFor(task.retryCount());
            dispatchDao.scheduleRetry(task.taskId(), now.plus(backoff), error, now);
        } else {
            // Retries exhausted — terminal FAILED. SEAM: task 9.2 publishes to reach.dlq from here.
            dispatchDao.markFailed(task.taskId(), "retries-exhausted:" + error, now);
        }
    }

    /** A stable-ish per-process lease owner id for {@code locked_by} (host + a random suffix). */
    private static String resolveWorkerId() {
        String host;
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            host = "unknown";
        }
        return host + "-" + Long.toHexString(ProcessHandle.current().pid());
    }

    /**
     * @return the lease owner id this dispatcher writes to {@code locked_by} (test visibility). Always
     *     non-null: {@link #resolveWorkerId()} falls back to {@code "unknown"} on an UnknownHost.
     */
    String workerId() {
        return workerId;
    }
}
