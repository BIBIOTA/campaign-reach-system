package com.example.campaignreach.reach.dispatcher;

import com.example.campaignreach.reach.channel.ChannelAdapter;
import com.example.campaignreach.reach.channel.NonRetryableSendException;
import com.example.campaignreach.reach.channel.ReachMessage;
import com.example.campaignreach.reach.channel.RetryableSendException;
import com.example.campaignreach.reach.channel.SendResult;
import com.example.campaignreach.reach.channel.SuppressionGuard;
import com.example.campaignreach.reach.channel.SuppressionVerdict;
import com.example.campaignreach.shared.event.Channel;
import com.example.campaignreach.shared.event.ReachTaskDeadLettered;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
 * <p><strong>Retry classification (task 9.2).</strong> A {@link NonRetryableSendException} (a permanent
 * provider failure such as an invalid address) fast-fails the task straight to FAILED without burning a
 * retry (state edge {@code PROCESSING --> FAILED : 不可重試}); a {@link RetryableSendException} backs off
 * via the exponential schedule; and a genuinely-unexpected {@code RuntimeException} is intentionally
 * routed through the retryable path so a single task's failure is isolated and the task is never lost.
 *
 * <p><strong>Retry-exhaustion → DLQ (task 9.2).</strong> When a task exhausts its bounded retries it is
 * dead-lettered, not silently dropped: the dispatcher publishes a {@link ReachTaskDeadLettered} event to
 * {@code reach.dlq} <em>first</em> and only then marks the row DLQ (publish-then-mark). If the publish
 * throws, the row is left PROCESSING with an expired lease for the Reaper (task 9.3) to reclaim, so the
 * task is never silently lost (「不靜默遺失」).
 *
 * <p><strong>Out of scope.</strong> The Reaper is task 9.3, and the campaign PAUSED/ENDED cancellation
 * re-check is task 10.1 — neither is implemented here. A consumer for {@code reach.dlq} (replay tooling)
 * is also out of scope; this only publishes.
 */
@Component
public class ReachTaskDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(ReachTaskDispatcher.class);

    private final ReachTaskDispatchDao dispatchDao;
    private final ChannelAdapterRegistry adapterRegistry;
    private final SuppressionGuard suppressionGuard;
    private final ObjectProvider<ReachDlqPublisher> dlqPublisher;
    private final DispatcherProperties properties;
    private final String workerId;

    /**
     * @param dispatchDao the two-phase claim / write-back persistence
     * @param adapterRegistry routes a channel to its adapter
     * @param suppressionGuard pre-send suppression check (a hit → non-retryable FAILED)
     * @param dlqPublisher the {@code reach.dlq} producer, looked up lazily: it is gated behind the
     *     Kafka at-least-once flag, so in a no-Kafka context it is absent and an exhausted task is left
     *     PROCESSING (rather than silently FAILED) for the Reaper to revisit — never lost
     * @param properties batch size + lease duration tunables
     */
    public ReachTaskDispatcher(
            ReachTaskDispatchDao dispatchDao,
            ChannelAdapterRegistry adapterRegistry,
            SuppressionGuard suppressionGuard,
            ObjectProvider<ReachDlqPublisher> dlqPublisher,
            DispatcherProperties properties) {
        this.dispatchDao = dispatchDao;
        this.adapterRegistry = adapterRegistry;
        this.suppressionGuard = suppressionGuard;
        this.dlqPublisher = dlqPublisher;
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
     * Dispatches one claimed task, isolating any failure from the rest of the batch. The outer broad
     * catch guarantees that a failure anywhere — including in a stage-two write-back or a DLQ publish —
     * never aborts the poll: the task simply remains PROCESSING with its lease for the Reaper (task 9.3)
     * to reclaim. This is the per-task isolation boundary (see class javadoc); the inner
     * {@link #dispatchTask} body does the classification and write-back.
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // deliberate broad catch: per-task exception isolation
    private void dispatch(ClaimedTask task, Instant now) {
        try {
            dispatchTask(task, now);
        } catch (RuntimeException isolated) {
            // A failure escaping the classification/write-back (e.g. a DLQ publish that threw, or a
            // write-back DB error): isolate it so the batch continues. The task stays PROCESSING with an
            // expired lease and is recovered by the Reaper — never silently lost (「不靜默遺失」).
            LOG.warn("Isolated failure finalizing reach_task {}: {}", task.taskId(), isolated.getMessage(), isolated);
        }
    }

    /**
     * Classifies and writes back one claimed task: pre-send suppression check, then the
     * out-of-transaction send, then the stage-two write-back (markSent / scheduleRetry / markFailed /
     * dead-letter). Exceptions thrown here are isolated by {@link #dispatch}.
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // deliberate broad catch: retryable fallback (see below)
    private void dispatchTask(ClaimedTask task, Instant now) {
        try {
            // Pre-send suppression (退訂 / 硬退信 / 投訴) is a non-retryable reason → FAILED, do not send.
            SuppressionVerdict verdict = suppressionGuard.evaluate(task.userId(), task.channel());
            if (verdict.suppressed()) {
                dispatchDao.markFailed(task.taskId(), workerId, "suppressed:" + verdict.reason(), now);
                return;
            }

            ChannelAdapter adapter = adapterRegistry
                    .forChannel(task.channel())
                    .orElseThrow(() -> new IllegalStateException("no ChannelAdapter for channel " + task.channel()));
            ReachMessage message = new ReachMessage(task.userId(), task.channel(), task.templateRef());
            SendResult result = adapter.send(message);
            dispatchDao.markSent(task.taskId(), workerId, result.providerMessageId(), now);
        } catch (NonRetryableSendException nonRetryable) {
            // Permanent provider failure (e.g. invalid address): fast-fail to FAILED immediately, no
            // retry burned (state edge PROCESSING --> FAILED : 不可重試). Distinct from the broad
            // RuntimeException fallback below, which stays routed to the retryable path on purpose.
            dispatchDao.markFailed(task.taskId(), workerId, "non-retryable:" + nonRetryable.getMessage(), now);
        } catch (RetryableSendException retryable) {
            // Retryable failure (transient provider error OR breaker short-circuit after PROCESSING):
            // stage-two write-back to RETRY_SCHEDULED with exponential backoff, or dead-letter once exhausted.
            writeBackRetryable(task, retryable.getMessage(), now);
        } catch (RuntimeException unexpected) {
            // A genuinely-unexpected exception is INTENTIONALLY routed through the retryable path so a
            // single task's failure is isolated (per-task isolation, see class javadoc) and the task is
            // never lost: it retries with backoff and converges to the DLQ once attempts are exhausted.
            // The explicit non-retryable taxonomy now lives in the NonRetryableSendException branch above;
            // an unclassified RuntimeException remains conservatively retryable so it is never lost.
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
     * once {@link RetryBackoffSchedule#MAX_ATTEMPTS} retries are exhausted — dead-letter the task.
     */
    private void writeBackRetryable(ClaimedTask task, String error, Instant now) {
        if (RetryBackoffSchedule.canRetry(task.retryCount())) {
            Duration backoff = RetryBackoffSchedule.backoffFor(task.retryCount());
            dispatchDao.scheduleRetry(task.taskId(), workerId, now.plus(backoff), error, now);
        } else {
            deadLetter(task, "retries-exhausted:" + error, now);
        }
    }

    /**
     * Dead-letter an exhausted task with at-least-once / 「不靜默遺失」 semantics: <strong>publish
     * first, then mark</strong>. The {@link ReachTaskDeadLettered} event is published to {@code
     * reach.dlq} synchronously (bounded wait, throws on failure); only if that succeeds is the row marked
     * DLQ. If the publish throws — or no publisher is wired (no-Kafka context) — the row is left
     * PROCESSING with its expired lease and the exception propagates through the per-task broad catch, so
     * the Reaper (task 9.3) revisits it and the task is never silently lost.
     */
    private void deadLetter(ClaimedTask task, String reason, Instant now) {
        ReachDlqPublisher publisher = dlqPublisher.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("no ReachDlqPublisher wired; cannot dead-letter reach_task " + task.taskId()
                    + " — leaving it PROCESSING for the Reaper rather than silently losing it");
        }
        ReachTaskDeadLettered event = new ReachTaskDeadLettered(
                task.taskId(),
                task.campaignId(),
                task.userId(),
                task.channel(),
                task.sendCycleKey(),
                reason,
                task.retryCount(),
                now);
        publisher.publish(event); // throws on failure/timeout → mark below is skipped, task not lost
        dispatchDao.markDeadLettered(task.taskId(), workerId, reason, now);
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
