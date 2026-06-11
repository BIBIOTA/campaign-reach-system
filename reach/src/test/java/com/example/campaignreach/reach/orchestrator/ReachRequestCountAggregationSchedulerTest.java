package com.example.campaignreach.reach.orchestrator;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Fast unit tests for {@link ReachRequestCountAggregationScheduler}. */
@ExtendWith(MockitoExtension.class)
class ReachRequestCountAggregationSchedulerTest {

    @Mock
    private ReachRequestCountAggregator aggregator;

    @InjectMocks
    private ReachRequestCountAggregationScheduler scheduler;

    /** One scheduler tick delegates to the set-based aggregator. */
    @Test
    void schedulerTickDelegatesToAggregator() {
        when(aggregator.aggregateCounts()).thenReturn(3);

        scheduler.aggregateCounts();

        verify(aggregator).aggregateCounts();
    }
}
