package com.example.campaignreach.reach.dispatcher;

import java.time.Duration;
import java.util.List;

/**
 * The bounded exponential-backoff schedule for retrying a failed {@code ReachTask} (task 9.1; spec
 * 「可靠發送與重試」). The schedule is {@code 1m → 5m → 30m} over at most {@value #MAX_ATTEMPTS} attempts:
 * after the third failure the task is exhausted and must terminate as {@code FAILED} rather than be
 * rescheduled.
 *
 * <p>This is a pure value type with no DB or clock dependency so the schedule and the
 * max-attempts boundary are unit-testable in isolation. {@code retryCount} here means "number of
 * retries already scheduled before this failure" — i.e. the count stored on the row when stage two
 * runs. A freshly-claimed PENDING task has {@code retryCount == 0}; its first failure schedules the
 * first retry (the {@code 1m} delay) and increments the stored count to 1.
 *
 * <p>Scope note: the retry <em>classification</em> taxonomy (which provider errors are retryable vs.
 * non-retryable) and the retry-exhaustion → DLQ leg are task 9.2; this type owns only the backoff
 * delays and the bounded-attempts terminal condition that 9.1's acceptance calls out.
 */
public final class RetryBackoffSchedule {

    /** Maximum number of retry attempts before a task is exhausted and marked FAILED. */
    public static final int MAX_ATTEMPTS = 3;

    /**
     * Backoff delays indexed by the retry attempt being scheduled: attempt 1 → 1m, attempt 2 → 5m,
     * attempt 3 → 30m.
     */
    private static final List<Duration> DELAYS =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30));

    private RetryBackoffSchedule() {}

    /**
     * Decides whether a task that has already been retried {@code retryCount} times may be retried
     * again after the current failure.
     *
     * @param retryCount the number of retries already scheduled before this failure (the value stored
     *     on the row; {@code 0} for a task that has not yet failed)
     * @return {@code true} while another retry remains within {@link #MAX_ATTEMPTS}; {@code false} once
     *     the attempts are exhausted (the task must terminate as FAILED)
     */
    public static boolean canRetry(int retryCount) {
        return retryCount < MAX_ATTEMPTS;
    }

    /**
     * The backoff delay before the next retry of a task that has already been retried {@code
     * retryCount} times.
     *
     * @param retryCount the number of retries already scheduled before this failure ({@code 0} → the
     *     first retry uses the {@code 1m} delay)
     * @return the delay to add to {@code now()} when computing the next {@code next_retry_at}
     * @throws IllegalStateException if the attempts are already exhausted (callers must check {@link
     *     #canRetry(int)} first)
     */
    public static Duration backoffFor(int retryCount) {
        if (!canRetry(retryCount)) {
            throw new IllegalStateException(
                    "no backoff for retryCount=" + retryCount + "; attempts exhausted (max " + MAX_ATTEMPTS + ")");
        }
        return DELAYS.get(retryCount);
    }
}
