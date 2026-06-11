package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campaignreach.reach.channel.SuppressionEntry;
import com.example.campaignreach.reach.channel.SuppressionGuard;
import com.example.campaignreach.reach.channel.SuppressionReason;
import com.example.campaignreach.reach.channel.SuppressionRepository;
import com.example.campaignreach.reach.channel.SuppressionVerdict;
import com.example.campaignreach.reach.orchestrator.ReachTaskStatus;
import com.example.campaignreach.shared.event.Channel;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistence-level integration test for the suppression-list lookup (task 8.2), backed by a real
 * PostgreSQL container with the Flyway-owned V7 schema applied. Auto-skipped without Docker via the
 * inherited {@link RequiresDocker} condition.
 *
 * <p>Unit tests ({@code SuppressionGuardTest}) mock the repository, so the JPA layer — the composite
 * {@code @IdClass} key and the {@code @JdbcTypeCode(NAMED_ENUM)} mapping of both the {@code channel}
 * and {@code suppression_reason} Postgres enums — is only really exercised here. Rows are inserted via
 * raw SQL (casting to the Postgres enum types) so the test verifies the read mapping the application
 * actually relies on, then asserts the full FR-015 / NFR-005 slice end-to-end through {@link
 * SuppressionGuard}: every reason maps, a hit yields FAILED-non-retryable, and the lookup is
 * channel-scoped.
 */
class SuppressionPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SuppressionRepository suppressionRepository;

    @Autowired
    private SuppressionGuard suppressionGuard;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM suppression");
    }

    private void insertSuppression(UUID userId, Channel channel, SuppressionReason reason) {
        jdbcTemplate.update(
                "INSERT INTO suppression (user_id, channel, reason, suppressed_at)"
                        + " VALUES (?, ?::channel, ?::suppression_reason, ?)",
                userId,
                channel.name(),
                reason.name(),
                Timestamp.from(Instant.now()));
    }

    /**
     * Scenario: 發送前抑制名單過濾 (FR-015) — each suppression_reason round-trips through the
     * {@code @JdbcTypeCode(NAMED_ENUM)} mapping and a hit yields a suppressed, non-retryable FAILED
     * verdict carrying that reason.
     */
    @Test
    void everySuppressionReasonRoundTripsAndYieldsFailedVerdict() {
        for (SuppressionReason reason : SuppressionReason.values()) {
            UUID userId = UUID.randomUUID();
            insertSuppression(userId, Channel.EMAIL, reason);

            // Read mapping: composite (user_id, channel) key + reason enum resolve from real columns.
            Optional<SuppressionEntry> entry = suppressionRepository.findByUserIdAndChannel(userId, Channel.EMAIL);
            assertThat(entry).isPresent();
            assertThat(entry.orElseThrow().getReason()).isEqualTo(reason);

            // End-to-end guard decision.
            SuppressionVerdict verdict = suppressionGuard.evaluate(userId, Channel.EMAIL);
            assertThat(verdict.suppressed()).isTrue();
            assertThat(verdict.reason()).isEqualTo(reason);
            assertThat(verdict.failedStatus()).isEqualTo(ReachTaskStatus.FAILED);
        }
    }

    /** Scenario: 未命中 → 准予發送 — a recipient with no suppression row is not suppressed. */
    @Test
    void recipientWithoutSuppressionRowIsNotSuppressed() {
        SuppressionVerdict verdict = suppressionGuard.evaluate(UUID.randomUUID(), Channel.EMAIL);

        assertThat(verdict.suppressed()).isFalse();
        assertThat(verdict.reason()).isNull();
    }

    /**
     * Scenario: 抑制名單為通道限定 (channel-scoped) — a user suppressed on SMS only is still sendable on
     * EMAIL. This verifies the composite key truly discriminates on channel against the real DB (not a
     * mock that never sees the SMS row).
     */
    @Test
    void suppressionOnOneChannelDoesNotSuppressAnotherForSameUser() {
        UUID userId = UUID.randomUUID();
        insertSuppression(userId, Channel.SMS, SuppressionReason.UNSUBSCRIBE);

        SuppressionVerdict smsVerdict = suppressionGuard.evaluate(userId, Channel.SMS);
        assertThat(smsVerdict.suppressed()).isTrue();
        assertThat(smsVerdict.reason()).isEqualTo(SuppressionReason.UNSUBSCRIBE);

        SuppressionVerdict emailVerdict = suppressionGuard.evaluate(userId, Channel.EMAIL);
        assertThat(emailVerdict.suppressed()).isFalse();
    }
}
