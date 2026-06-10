package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.campaignreach.campaign.domain.Campaign;
import com.example.campaignreach.campaign.domain.CampaignRepository;
import com.example.campaignreach.campaign.domain.CampaignStatus;
import com.example.campaignreach.campaign.domain.CampaignType;
import com.example.campaignreach.campaign.domain.CurrentOperator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Persistence-level integration test for the Campaign aggregate (task 3.1), backed
 * by a real PostgreSQL container with the Flyway-owned schema applied. Auto-skipped
 * without Docker via the inherited {@link RequiresDocker} condition.
 *
 * <p>Verifies the spec §4 / FR-001 scenarios: optimistic-lock concurrency control,
 * audit-field recording, and exact persistence of the status/type enum values.
 */
class CampaignPersistenceIntegrationTest extends AbstractIntegrationTest {

    private static final UUID OPERATOR_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OPERATOR_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Autowired
    private CampaignRepository repository;

    @AfterEach
    void clearOperator() {
        CurrentOperator.clear();
    }

    private Campaign newCampaign() {
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new Campaign(
                UUID.randomUUID(),
                "Spring Sale",
                CampaignType.DISCOUNT,
                CampaignStatus.DRAFT,
                start,
                start.plus(7, ChronoUnit.DAYS),
                "{\"percentOff\":10}",
                "{\"segment\":\"vip\"}",
                "{\"channel\":\"EMAIL\"}");
    }

    /**
     * Scenario: 兩名營運同時編輯 — two operators read the same campaign, then commit in
     * turn; the optimistic-lock version makes the later writer fail (reload prompt).
     */
    @Test
    void twoOperatorsEditingSameCampaign_laterWriterFailsOnStaleVersion() {
        CurrentOperator.set(OPERATOR_A);
        Campaign persisted = repository.saveAndFlush(newCampaign());
        UUID id = persisted.getId();

        // Both operators read the same snapshot (same version).
        Campaign readByA = repository.findById(id).orElseThrow();
        Campaign readByB = repository.findById(id).orElseThrow();
        assertThat(readByA.getVersion()).isEqualTo(readByB.getVersion());

        // Operator A commits first and succeeds, bumping the version.
        CurrentOperator.set(OPERATOR_A);
        readByA.setName("Renamed by A");
        repository.saveAndFlush(readByA);

        // Operator B commits a stale snapshot: optimistic lock rejects the later write.
        CurrentOperator.set(OPERATOR_B);
        readByB.setName("Renamed by B");
        assertThatThrownBy(() -> repository.saveAndFlush(readByB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // A's write is the one that stuck.
        assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo("Renamed by A");
    }

    /**
     * Scenario: 成功寫入者記錄 updated_by 與 updated_at — successful writes populate the
     * audit columns, and an update records the updating operator and a later
     * updated_at than the original create.
     */
    @Test
    void successfulWriteRecordsUpdatedByAndUpdatedAt() {
        CurrentOperator.set(OPERATOR_A);
        Campaign created = repository.saveAndFlush(newCampaign());
        UUID id = created.getId();

        assertThat(created.getCreatedBy()).isEqualTo(OPERATOR_A);
        assertThat(created.getUpdatedBy()).isEqualTo(OPERATOR_A);
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        Instant createdAt = created.getCreatedAt();

        Campaign reread = repository.findById(id).orElseThrow();
        CurrentOperator.set(OPERATOR_B);
        reread.setName("Edited");
        Campaign updated = repository.saveAndFlush(reread);

        assertThat(updated.getCreatedBy()).isEqualTo(OPERATOR_A);
        assertThat(updated.getUpdatedBy()).isEqualTo(OPERATOR_B);
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    /** Every CampaignStatus enum value round-trips through the PostgreSQL enum column. */
    @Test
    void allStatusEnumValuesPersistExactly() {
        CurrentOperator.set(OPERATOR_A);
        for (CampaignStatus status : CampaignStatus.values()) {
            Campaign c = newCampaign();
            c.setStatus(status);
            Campaign saved = repository.saveAndFlush(c);
            assertThat(repository.findById(saved.getId()).orElseThrow().getStatus())
                    .isEqualTo(status);
        }
        assertThat(CampaignStatus.values())
                .containsExactlyInAnyOrder(
                        CampaignStatus.DRAFT,
                        CampaignStatus.SCHEDULED,
                        CampaignStatus.RUNNING,
                        CampaignStatus.PAUSED,
                        CampaignStatus.ENDED);
    }

    /** Every CampaignType enum value round-trips through the PostgreSQL enum column. */
    @Test
    void allTypeEnumValuesPersistExactly() {
        CurrentOperator.set(OPERATOR_A);
        for (CampaignType type : CampaignType.values()) {
            Campaign c = newCampaign();
            c.setType(type);
            Campaign saved = repository.saveAndFlush(c);
            assertThat(repository.findById(saved.getId()).orElseThrow().getType())
                    .isEqualTo(type);
        }
        assertThat(CampaignType.values())
                .containsExactlyInAnyOrder(CampaignType.DISCOUNT, CampaignType.GIFT_ADDON, CampaignType.FLASH_SALE);
    }
}
