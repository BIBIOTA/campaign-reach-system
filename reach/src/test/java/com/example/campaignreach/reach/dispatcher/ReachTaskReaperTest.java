package com.example.campaignreach.reach.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Behaviour-driven tests for {@link ReachTaskReaper} (task 9.3, scenario「回收卡死任務」). The DAO is
 * mocked to verify the Reaper delegates the reset to {@link ReachTaskDispatchDao#reapExpiredLeases}
 * with the tick instant and tolerates the returned count (including the no-op zero case).
 */
@ExtendWith(MockitoExtension.class)
class ReachTaskReaperTest {

    @Mock
    private ReachTaskDispatchDao dispatchDao;

    @InjectMocks
    private ReachTaskReaper reaper;

    /** A reap tick resets expired-lease PROCESSING tasks via the DAO, passing a current cutoff instant. */
    @Test
    void reapTickResetsExpiredLeasesViaDaoWithCurrentCutoff() {
        when(dispatchDao.reapExpiredLeases(org.mockito.ArgumentMatchers.any())).thenReturn(2);
        Instant before = Instant.now();

        reaper.reapExpiredLeases();

        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(dispatchDao).reapExpiredLeases(nowCaptor.capture());
        assertThat(nowCaptor.getValue()).isBetween(before, Instant.now());
    }

    /** A tick that finds nothing stuck (count 0) completes cleanly without error. */
    @Test
    void reapTickWithNothingStuckCompletesCleanly() {
        when(dispatchDao.reapExpiredLeases(org.mockito.ArgumentMatchers.any())).thenReturn(0);

        reaper.reapExpiredLeases();

        verify(dispatchDao).reapExpiredLeases(org.mockito.ArgumentMatchers.any());
    }
}
