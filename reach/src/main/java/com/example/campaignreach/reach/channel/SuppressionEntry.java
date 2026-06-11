package com.example.campaignreach.reach.channel;

import com.example.campaignreach.shared.event.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A suppression-list row: a recipient who must NOT be sent on a given channel (ER {@code suppression};
 * design.md §10「退訂與抑制名單」; FR-015, NFR-005).
 *
 * <p>Read-only lookup entity for the pre-send {@link SuppressionGuard} check. The composite key is
 * {@code (user_id, channel)} (mapped via {@code @IdClass} {@link SuppressionEntryId}), so suppression
 * is <strong>channel-scoped</strong> — a user suppressed on SMS is still sendable on EMAIL. Only
 * {@code user_id} is stored (no recipient email; PII minimization, NFR-005). The {@link
 * SuppressionReason} (退訂 / 硬退信 / 投訴) is always a non-retryable reason.
 *
 * <p>The maintenance and source of these rows belong to the upstream e-commerce site; this system only
 * consumes them. Like {@code AudienceListMember}, this entity does not throw in its constructor (the
 * rows are curated lookup data, not a domain aggregate with creation invariants), so no
 * {@code CT_CONSTRUCTOR_THROW} SpotBugs exclusion is needed.
 */
@Entity
@Table(name = "suppression")
@IdClass(SuppressionEntryId.class)
public class SuppressionEntry {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "channel", nullable = false, updatable = false, columnDefinition = "channel")
    private Channel channel;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reason", nullable = false, columnDefinition = "suppression_reason")
    private SuppressionReason reason;

    @Column(name = "suppressed_at", nullable = false, updatable = false)
    private Instant suppressedAt;

    /** JPA requires a no-arg constructor. */
    protected SuppressionEntry() {}

    public UUID getUserId() {
        return userId;
    }

    public Channel getChannel() {
        return channel;
    }

    public SuppressionReason getReason() {
        return reason;
    }

    public Instant getSuppressedAt() {
        return suppressedAt;
    }
}
