package com.example.campaignreach.reach.dispatcher;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
 */
@ConfigurationProperties(prefix = "campaignreach.reach.retention")
public record RetentionProperties(Duration period) {

    /** Validates the retention period: present and strictly positive — never silently permanent. */
    public RetentionProperties {
        if (period == null) {
            throw new IllegalArgumentException("campaignreach.reach.retention.period must be set "
                    + "(NFR-005: a data-retention period is required and must never silently default to "
                    + "permanent retention)");
        }
        if (period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("campaignreach.reach.retention.period must be positive");
        }
    }
}
