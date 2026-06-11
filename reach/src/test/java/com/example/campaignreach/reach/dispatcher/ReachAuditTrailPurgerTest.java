package com.example.campaignreach.reach.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Fast SQL-shape / cutoff tests for {@link ReachAuditTrailPurger} (task 11.1, scenario「觸達稽核軌跡屆期
 * 歸檔或刪除」). The real FK-ordered DELETE against Postgres is exercised in the Docker-gated integration
 * test; here the cutoff math, FK ordering, and terminal-state guard predicate are pinned cheaply.
 */
@ExtendWith(MockitoExtension.class)
class ReachAuditTrailPurgerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ReachAuditTrailPurger purger;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        purger = new ReachAuditTrailPurger(
                jdbcTemplate, transactionManager, new RetentionProperties(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("以 now - 保留期限 為截止點，先刪 send_result 子列再刪 reach_task，且僅限終態")
    void deletesChildrenBeforeParentsWithTerminalGuardAtCutoff() {
        when(jdbcTemplate.update(anyString(), any(Object.class))).thenReturn(3);
        Instant now = Instant.parse("2026-06-11T00:00:00Z");

        int purged = purger.purgeExpired(now);

        assertThat(purged).isEqualTo(3);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> cutoff = ArgumentCaptor.forClass(Object.class);
        // Two guarded deletes were issued; capture both (in invocation order) to assert FK ordering.
        verify(jdbcTemplate, times(2)).update(sql.capture(), cutoff.capture());

        assertThat(sql.getAllValues().get(0))
                .contains("DELETE FROM send_result")
                .contains("reach_task_id IN")
                .contains("'SENT'::reach_task_status")
                .contains("'CANCELLED'::reach_task_status");
        assertThat(sql.getAllValues().get(1))
                .contains("DELETE FROM reach_task")
                .contains("created_at < ?")
                .contains("'FAILED'::reach_task_status")
                .contains("'DLQ'::reach_task_status");
        // In-flight statuses must never appear in the purge predicate.
        assertThat(sql.getAllValues().get(1)).doesNotContain("PENDING").doesNotContain("PROCESSING");

        Timestamp expectedCutoff = Timestamp.from(now.minus(Duration.ofDays(30)));
        assertThat(cutoff.getAllValues()).containsExactly(expectedCutoff, expectedCutoff);
    }

    @Test
    @DisplayName("無屆期資料時回傳 0、不報錯")
    void returnsZeroWhenNothingExpired() {
        when(jdbcTemplate.update(anyString(), any(Object.class))).thenReturn(0);

        int purged = purger.purgeExpired(Instant.now());

        assertThat(purged).isZero();
    }
}
