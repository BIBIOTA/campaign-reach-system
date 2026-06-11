package com.example.campaignreach.reach.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Fast SQL-shape tests for {@link ReachRequestCountAggregator}. */
@ExtendWith(MockitoExtension.class)
class ReachRequestCountAggregatorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ReachRequestCountAggregator aggregator;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        aggregator = new ReachRequestCountAggregator(jdbcTemplate, transactionManager);
    }

    /** Aggregation is bounded to active/incomplete batches and joins tasks by reach_request_id. */
    @Test
    void aggregateCountsUsesActiveBatchPredicateAndRequestLeadingJoin() {
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.anyString())).thenReturn(4);

        int updated = aggregator.aggregateCounts();

        assertThat(updated).isEqualTo(4);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture());
        assertThat(sql.getValue())
                .contains("WITH active_requests AS")
                .contains("status IN ('EXPANDING'::reach_request_status, 'DISPATCHING'::reach_request_status)")
                .contains("LEFT JOIN reach_task t ON t.reach_request_id = active_requests.id")
                .contains("GROUP BY active_requests.id");
    }
}
