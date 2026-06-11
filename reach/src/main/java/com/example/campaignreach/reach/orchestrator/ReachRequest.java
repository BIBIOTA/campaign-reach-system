package com.example.campaignreach.reach.orchestrator;

import com.example.campaignreach.shared.event.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Activity-level reach batch (ER {@code reach_request}; spec §5, FR-013, NFR-003).
 *
 * <p>The reach orchestrator lands exactly one row per campaign trigger, deduplicated by the
 * {@code unique(campaign_id, send_cycle_key, trigger_type)} key, so Kafka at-least-once redelivery of
 * the same {@link com.example.campaignreach.shared.event.ReachRequested} never creates a second batch
 * nor re-pollutes the counts. The {@code target_spec_snapshot} / {@code reach_plan_snapshot} are
 * <strong>frozen</strong> at creation time (the verbatim JSON the event carried), so a campaign edited
 * afterwards remains traceable to the basis the batch was sent on.
 *
 * <p><strong>Naming alignment (§5).</strong> The event's camelCase {@code sendCycle} maps verbatim to
 * the snake_case {@code send_cycle_key} column — same value, different naming style.
 *
 * <p><strong>Scope.</strong> Task 7.1 lands the batch in {@link ReachRequestStatus#PENDING} with frozen
 * snapshots. The {@code total_count}/{@code pending_count}/{@code sent_count}/{@code failed_count}
 * counters and the {@code started_at}/{@code finished_at} expansion timestamps exist in the
 * {@code reach_request} table (created by the V4 migration per the ER contract) but are written by
 * audience fan-out (task 7.3); they are deliberately not mapped on this entity yet to keep 7.1 minimal.
 * Snapshots are stored as raw JSONB ({@link String}), mirroring the Campaign aggregate.
 */
@Entity
@Table(name = "reach_request")
public class ReachRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "trigger_type", nullable = false, updatable = false, columnDefinition = "trigger_type")
    private TriggerType triggerType;

    @Column(name = "trigger_event_id", updatable = false)
    private String triggerEventId;

    @Column(name = "send_cycle_key", nullable = false, updatable = false)
    private String sendCycleKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_spec_snapshot", updatable = false)
    private String targetSpecSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reach_plan_snapshot", updatable = false)
    private String reachPlanSnapshot;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "reach_request_status")
    private ReachRequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA requires a no-arg constructor. */
    protected ReachRequest() {}

    /**
     * Creates a freshly landed reach batch in {@link ReachRequestStatus#PENDING} with frozen snapshots.
     *
     * @param id batch identity
     * @param campaignId owning campaign
     * @param triggerType source of the trigger (part of the dedup key)
     * @param triggerEventId source event id; non-null only for {@link TriggerType#EVENT}
     * @param sendCycleKey send-cycle key (part of the dedup key; same value as the event's sendCycle)
     * @param targetSpecSnapshot frozen targeting-condition JSON snapshot
     * @param reachPlanSnapshot frozen channel/template/timing JSON snapshot
     * @param createdAt batch creation time
     */
    public ReachRequest(
            UUID id,
            UUID campaignId,
            TriggerType triggerType,
            String triggerEventId,
            String sendCycleKey,
            String targetSpecSnapshot,
            String reachPlanSnapshot,
            Instant createdAt) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (campaignId == null) throw new IllegalArgumentException("campaignId must not be null");
        if (triggerType == null) throw new IllegalArgumentException("triggerType must not be null");
        if (sendCycleKey == null || sendCycleKey.isBlank())
            throw new IllegalArgumentException("sendCycleKey must not be null or blank");
        if (createdAt == null) throw new IllegalArgumentException("createdAt must not be null");
        this.id = id;
        this.campaignId = campaignId;
        this.triggerType = triggerType;
        this.triggerEventId = triggerEventId;
        this.sendCycleKey = sendCycleKey;
        this.targetSpecSnapshot = targetSpecSnapshot;
        this.reachPlanSnapshot = reachPlanSnapshot;
        this.status = ReachRequestStatus.PENDING;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public String getTriggerEventId() {
        return triggerEventId;
    }

    public String getSendCycleKey() {
        return sendCycleKey;
    }

    public String getTargetSpecSnapshot() {
        return targetSpecSnapshot;
    }

    public String getReachPlanSnapshot() {
        return reachPlanSnapshot;
    }

    public ReachRequestStatus getStatus() {
        return status;
    }

    public void setStatus(ReachRequestStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
