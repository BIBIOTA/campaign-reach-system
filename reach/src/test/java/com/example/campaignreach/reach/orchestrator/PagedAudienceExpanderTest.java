package com.example.campaignreach.reach.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.campaignreach.reach.audience.AudienceResolver;
import com.example.campaignreach.reach.audience.Recipient;
import com.example.campaignreach.reach.audience.TargetSpec;
import com.example.campaignreach.reach.audience.TargetSpecParser;
import com.example.campaignreach.shared.event.Channel;
import com.example.campaignreach.shared.event.TriggerType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Fast unit tests for {@link PagedAudienceExpander} (task 7.3; spec §5 scenarios "一筆請求展開成 N 筆任務",
 * "斷點續跑", "同一人同週期只建立一筆任務", "短時間頻控跳過"). No Spring context, no DB: the resolver, parser,
 * channel extractor and {@link JdbcTemplate} are mocked, and the {@code TransactionTemplate}
 * executes its callback inline. Behaviour is asserted on the actual SQL the expander issues.
 */
@ExtendWith(MockitoExtension.class)
class PagedAudienceExpanderTest {

    private static final UUID REQUEST_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SEND_CYCLE = "sched:" + CAMPAIGN_ID + ":2026-06-11T14:00:00Z";
    private static final String TARGET_SPEC = "{\"kind\":\"STATIC_LIST\",\"listId\":\"abc\"}";
    private static final String REACH_PLAN = "{\"channel\":\"EMAIL\",\"templateRef\":\"welcome\"}";

    @Mock
    private TargetSpecParser targetSpecParser;

    @Mock
    private ReachPlanChannelExtractor reachPlanChannelExtractor;

    @Mock
    private AudienceResolver audienceResolver;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private PagedAudienceExpander expander;

    @BeforeEach
    void setUp() {
        // Real TransactionTemplate over a mock tx manager: getTransaction returns a status,
        // commit/rollback are no-ops, so the per-page callback runs inline against the mock JdbcTemplate.
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ExpansionProperties props = new ExpansionProperties(
                new ExpansionProperties.Expansion(2), // tiny page size to force multi-page fan-out
                new ExpansionProperties.FrequencyCap(java.time.Duration.ofHours(24)));
        expander = new PagedAudienceExpander(
                targetSpecParser, reachPlanChannelExtractor, audienceResolver, jdbcTemplate, transactionManager, props);
    }

    private ReachRequest landedBatch(ReachRequestStatus status) {
        ReachRequest batch = new ReachRequest(
                REQUEST_ID,
                CAMPAIGN_ID,
                TriggerType.SCHEDULED_BATCH,
                null,
                SEND_CYCLE,
                TARGET_SPEC,
                REACH_PLAN,
                Instant.now());
        batch.setStatus(status);
        return batch;
    }

    private List<Recipient> recipients(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> new Recipient(UUID.randomUUID()))
                .collect(Collectors.toList());
    }

    private void stubResolution(List<Recipient> recipients) {
        when(targetSpecParser.parse(TARGET_SPEC))
                .thenReturn(new TargetSpec(TargetSpec.Kind.STATIC_LIST, UUID.randomUUID(), java.util.Map.of()));
        when(reachPlanChannelExtractor.extract(REACH_PLAN)).thenReturn(Channel.EMAIL);
        when(audienceResolver.resolve(any(TargetSpec.class))).thenReturn(recipients);
    }

    /** No user is frequency-capped (EXISTS returns false for every recipient). */
    private void noFrequencyCap() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(), any(), any()))
                .thenReturn(Boolean.FALSE);
    }

    @Nested
    @DisplayName("一筆請求展開成 N 筆任務")
    class ExpandsIntoManyTasks {

        @Test
        @DisplayName("N 位收件人分頁批次 INSERT N 筆，status PENDING→EXPANDING→DISPATCHING 並一次回填 total_count")
        void recipientsBecomeBatchedTasksAndStatusAdvances() {
            stubResolution(recipients(5)); // page size 2 -> pages of 2,2,1
            noFrequencyCap();

            expander.expand(landedBatch(ReachRequestStatus.PENDING));

            // PENDING -> EXPANDING then EXPANDING -> DISPATCHING (+ total_count backfilled once).
            // The two status-advance updates carry varargs bind params; match the whole vararg array.
            ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate, times(2)).update(updateSql.capture(), any(Object[].class));
            assertThat(updateSql.getAllValues())
                    .anyMatch(sql -> sql.contains("'EXPANDING'") && sql.contains("started_at"));
            assertThat(updateSql.getAllValues())
                    .anyMatch(sql -> sql.contains("'DISPATCHING'") && sql.contains("total_count"));

            // Three pages -> three ON CONFLICT batchUpdate calls; surviving rows total 5.
            ArgumentCaptor<List<Object[]>> batches = batchCaptor();
            verify(jdbcTemplate, times(3)).batchUpdate(contains("ON CONFLICT"), batches.capture());
            int inserted = batches.getAllValues().stream().mapToInt(List::size).sum();
            assertThat(inserted).isEqualTo(5);
            // Every insert row carries PENDING status and the EMAIL channel (dedup-key column).
            batches.getAllValues().stream().flatMap(List::stream).forEach(row -> {
                assertThat(row[5]).isEqualTo(Channel.EMAIL.name());
                assertThat(row[6]).isEqualTo(ReachTaskStatus.PENDING.name());
                assertThat(row[4]).isEqualTo(SEND_CYCLE);
            });
        }
    }

    @Nested
    @DisplayName("斷點續跑")
    class ResumesFromExpanding {

        @Test
        @DisplayName("已 EXPANDING（重投/續跑）：再次 expand 不報錯，仍以 ON CONFLICT 續寫")
        void alreadyExpandingResumesWithoutError() {
            stubResolution(recipients(3));
            noFrequencyCap();

            expander.expand(landedBatch(ReachRequestStatus.EXPANDING));

            // The PENDING->EXPANDING update is still issued (guarded by WHERE status=PENDING, so it is a
            // harmless no-op on resume); inserts still run via ON CONFLICT so the remainder converges.
            verify(jdbcTemplate, atLeastOnce()).batchUpdate(contains("ON CONFLICT"), anyList());
        }

        @Test
        @DisplayName("INSERT 使用 ON CONFLICT (...) DO NOTHING 於四欄 unique key，重投不重複建立")
        void insertUsesOnConflictDoNothingOnFourColumnKey() {
            stubResolution(recipients(1));
            noFrequencyCap();

            expander.expand(landedBatch(ReachRequestStatus.PENDING));

            verify(jdbcTemplate)
                    .batchUpdate(
                            contains("ON CONFLICT (campaign_id, user_id, send_cycle_key, channel) DO NOTHING"),
                            anyList());
        }
    }

    @Nested
    @DisplayName("短時間頻控跳過")
    class FrequencyCapSkips {

        @Test
        @DisplayName("不同週期窗口內已觸達者被跳過，未被頻控者照常 INSERT（頻控與冪等分離）")
        void usersReachedInDifferentCycleWithinWindowAreSkipped() {
            List<Recipient> recipients = recipients(3);
            stubResolution(recipients);
            // Cap the SECOND recipient only (true once, false otherwise).
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(), any(), any()))
                    .thenReturn(Boolean.FALSE, Boolean.TRUE, Boolean.FALSE);

            expander.expand(landedBatch(ReachRequestStatus.PENDING));

            // The frequency-cap predicate keys on user_id AND a DIFFERENT send_cycle (<> current).
            verify(jdbcTemplate, atLeastOnce())
                    .queryForObject(contains("send_cycle_key <> ?"), eq(Boolean.class), any(), eq(SEND_CYCLE), any());
            // Only 2 of 3 survive the cap (page size 2 -> page1 inserts 1 survivor, page2 inserts 1).
            ArgumentCaptor<List<Object[]>> batches = batchCaptor();
            verify(jdbcTemplate, atLeastOnce()).batchUpdate(contains("ON CONFLICT"), batches.capture());
            int inserted = batches.getAllValues().stream().mapToInt(List::size).sum();
            assertThat(inserted).isEqualTo(2);
        }

        @Test
        @DisplayName("整頁全被頻控時不發出空的 batchUpdate")
        void emptyPageAfterCapIssuesNoBatchUpdate() {
            stubResolution(recipients(1));
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(), any(), any()))
                    .thenReturn(Boolean.TRUE);

            expander.expand(landedBatch(ReachRequestStatus.PENDING));

            verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList());
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Object[]>> batchCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    /** Mockito {@code contains} returns null; wrap so the matcher reads naturally inline. */
    private static String contains(String substring) {
        return org.mockito.ArgumentMatchers.contains(substring);
    }
}
