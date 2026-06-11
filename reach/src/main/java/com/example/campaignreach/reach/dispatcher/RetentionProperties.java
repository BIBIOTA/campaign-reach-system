package com.example.campaignreach.reach.dispatcher;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configurable data-retention policy for the reach audit trail (task 11.1, spec「收件人 PII 最小化與抑制名單」
 * scenario「保留策略存在且可設定」, NFR-005), bound from {@code campaignreach.reach.retention.*}.
 *
 * <p><strong>Never silently permanent.</strong> The retention period MUST be present and configurable;
 * NFR-005 forbids silently defaulting to「永久保留」. This record therefore fails fast in its compact
 * constructor when {@code period} is {@code null} (mirroring the {@code
 * RequiredInfrastructurePropertiesValidator}「fail loud, never silently degrade」stance of §1.2): a
 * missing period halts application startup with a clear error rather than degrading to infinite
 * retention. A non-positive period (zero / negative) is likewise rejected. A non-permanent default is
 * supplied in {@code application.yml} so the app still boots in dev; the exact month count is an Open
 * Question pending legal/compliance confirmation (see proposal.md ## Open Questions).
 *
 * @param period how long {@code reach_task} / {@code send_result} audit rows are retained before the
 *     purge job ({@link ReachAuditTrailPurger}) deletes them; measured from {@code reach_task.created_at}.
 *     Required and strictly positive — never permanent.
 * @param purgeBatchSize maximum {@code reach_task} rows deleted per purge transaction. The purge loops
 *     in batches of this size so a single sweep never holds a long lock / accumulates a huge WAL over the
 *     full expired set at scale (100k+ rows); each batch commits independently. Optional — a {@code null}
 *     value defaults to {@link #DEFAULT_PURGE_BATCH_SIZE}; a configured non-positive value is rejected.
 */
@ConfigurationProperties(prefix = "campaignreach.reach.retention")
public record RetentionProperties(Duration period, Integer purgeBatchSize) {

    /** Default per-transaction purge batch when {@code purge-batch-size} is left unset. */
    public static final int DEFAULT_PURGE_BATCH_SIZE = 1000;

    /**
     * Validates the retention period (present and strictly positive — never silently permanent) and
     * normalizes the purge batch size (default when unset, reject a configured non-positive value).
     *
     * <p>{@code @ConstructorBinding} disambiguates this canonical constructor as the one Spring Boot
     * binds {@code campaignreach.reach.retention.*} through, now that a second (convenience) constructor
     * also exists.
     */
    @ConstructorBinding
    public RetentionProperties {
        if (period == null) {
            throw new IllegalArgumentException("campaignreach.reach.retention.period must be set "
                    + "(NFR-005: a data-retention period is required and must never silently default to "
                    + "permanent retention)");
        }
        if (period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("campaignreach.reach.retention.period must be positive");
        }
        if (purgeBatchSize == null) {
            purgeBatchSize = DEFAULT_PURGE_BATCH_SIZE;
        } else if (purgeBatchSize <= 0) {
            throw new IllegalArgumentException("campaignreach.reach.retention.purge-batch-size must be positive");
        }
    }

    /**
     * Convenience factory used by tests / callers that only care about the retention period; the purge
     * batch size falls back to {@link #DEFAULT_PURGE_BATCH_SIZE}.
     *
     * @param period the (required, strictly-positive) retention period
     */
    public RetentionProperties(Duration period) {
        this(period, null);
    }
}
