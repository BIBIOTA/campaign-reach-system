package com.example.campaignreach.reach.channel;

import com.example.campaignreach.shared.event.Channel;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Component;

/**
 * {@link ChannelAdapter} for the EMAIL channel, wrapping the external Email provider in a circuit
 * breaker (spec §4 FR-009; §6 NFR-004 "外部通道中斷的穩定降級").
 *
 * <p>Delivery flows {@link EmailProviderClient} ← wrapped by → {@link CircuitBreaker}. The breaker
 * (configured via {@code EmailChannelProperties} → {@code emailChannelBreaker} bean) gives the
 * NFR-004 recovery contract: it OPENs on a configurable failure-rate threshold over a sliding
 * window, stays open for a configurable cool-down, then HALF-OPENs to admit a configurable number
 * of probes, returning to CLOSED only if every probe succeeds.
 *
 * <p>Failure translation is the dispatcher seam (Section 9 does the write-back):
 *
 * <ul>
 *   <li>A breaker short-circuit ({@link CallNotPermittedException}) → {@link
 *       RetryableSendException#breakerOpen} ({@code isBreakerOpen() == true}). A future dispatcher
 *       can query {@link #isAvailable()} before claiming a task and skip it while leaving it PENDING
 *       (scenario「breaker 開啟時不卡任務」), or, if already PROCESSING, treat the fast-fail as a
 *       retryable failure → RETRY_SCHEDULED (scenario「已 PROCESSING 後 breaker 失敗」).
 *   <li>A real provider failure → {@link RetryableSendException#providerFailure} and the breaker
 *       counts it toward its failure rate.
 * </ul>
 */
@Component
public class EmailAdapter implements ChannelAdapter {

    private final EmailProviderClient providerClient;
    private final CircuitBreaker circuitBreaker;

    /**
     * @param providerClient the wrapped external Email provider (SendGrid/SES) seam
     * @param circuitBreaker the configured breaker guarding the provider call (bean {@code
     *     emailChannelBreaker})
     */
    public EmailAdapter(EmailProviderClient providerClient, CircuitBreaker circuitBreaker) {
        this.providerClient = providerClient;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    // The broad catch(RuntimeException) is deliberate failure isolation: ANY provider-side failure
    // (timeout, transport error, SDK runtime exception) must be translated into a single retryable
    // signal so the dispatcher's retry path is uniform and the breaker counts it. Re-throwing would
    // leak provider-specific exception types across the channel-adapter seam (spec §4 FR-009; §6
    // NFR-004). This is the documented, justified IllegalCatch case (see CLAUDE.md).
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public SendResult send(ReachMessage message) {
        if (message.channel() != Channel.EMAIL) {
            throw new IllegalArgumentException(
                    "EmailAdapter cannot send channel " + message.channel() + "; expected EMAIL");
        }
        try {
            return circuitBreaker.executeSupplier(() -> providerClient.deliver(message));
        } catch (CallNotPermittedException breakerRejected) {
            // Breaker is OPEN (or a half-open probe was rejected): the provider was never contacted.
            throw RetryableSendException.breakerOpen(
                    "email circuit breaker is open; skipping provider call", breakerRejected);
        } catch (RuntimeException providerFailure) {
            // The provider call itself failed; the breaker has already recorded it as a failure.
            throw RetryableSendException.providerFailure("email provider call failed", providerFailure);
        }
    }

    /**
     * @return {@code true} if the breaker is not OPEN (i.e. CLOSED or HALF_OPEN), meaning a send is
     *     worth attempting; {@code false} while it is OPEN. A future dispatcher (Section 9) calls
     *     this before marking a task {@code PROCESSING} so an open breaker leaves the task PENDING
     *     for a later re-scan instead of failing it (scenario「breaker 開啟時不卡任務」). This is a
     *     non-consuming state read — it does not acquire a breaker permit, so it never perturbs the
     *     sliding-window accounting.
     */
    public boolean isAvailable() {
        return circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }
}
