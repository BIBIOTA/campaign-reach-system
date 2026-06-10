package com.example.campaignreach.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Campaign aggregate root (class-model {@code Campaign}; ER {@code campaign}).
 *
 * <p>Persists the campaign identity, lifecycle status, offer type, active period
 * and the raw offer/targeting/reach JSON (spec §4). Concurrent edits are guarded
 * by an optimistic-lock {@link #version}: the later writer of a stale version
 * fails with {@code OptimisticLockException} (FR-001). Audit columns
 * ({@code created_by} / {@code updated_by} / {@code created_at} / {@code updated_at})
 * are populated automatically by Spring Data JPA auditing.
 *
 * <p>Scope note (task 3.1): {@code ruleConfig} / {@code targetSpec} / {@code reachPlan}
 * are stored as raw JSONB ({@link String}) without per-type DTO validation — that
 * validation and the RuleConfig upcaster arrive in task 3.2.
 */
@Entity
@Table(name = "campaign")
@EntityListeners(AuditingEntityListener.class)
public class Campaign {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, columnDefinition = "campaign_type")
    private CampaignType type;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "campaign_status")
    private CampaignStatus status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_config")
    private String ruleConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_spec")
    private String targetSpec;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reach_plan")
    private String reachPlan;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA requires a no-arg constructor. */
    protected Campaign() {}

    /**
     * Creates a campaign in its initial persistent state. Audit columns and the
     * optimistic-lock version are managed by JPA, not supplied here.
     */
    public Campaign(
            UUID id,
            String name,
            CampaignType type,
            CampaignStatus status,
            Instant startAt,
            Instant endAt,
            String ruleConfig,
            String targetSpec,
            String reachPlan) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be null or blank");
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (startAt == null) throw new IllegalArgumentException("startAt must not be null");
        if (endAt == null) throw new IllegalArgumentException("endAt must not be null");
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
        this.startAt = startAt;
        this.endAt = endAt;
        this.ruleConfig = ruleConfig;
        this.targetSpec = targetSpec;
        this.reachPlan = reachPlan;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CampaignType getType() {
        return type;
    }

    public void setType(CampaignType type) {
        this.type = type;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public String getRuleConfig() {
        return ruleConfig;
    }

    public void setRuleConfig(String ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    public String getTargetSpec() {
        return targetSpec;
    }

    public void setTargetSpec(String targetSpec) {
        this.targetSpec = targetSpec;
    }

    public String getReachPlan() {
        return reachPlan;
    }

    public void setReachPlan(String reachPlan) {
        this.reachPlan = reachPlan;
    }

    public int getVersion() {
        return version;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
