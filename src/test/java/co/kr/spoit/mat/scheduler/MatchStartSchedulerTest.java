package co.kr.spoit.mat.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.service.TeamAssignmentService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchStartSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 18, 0);

    @Mock
    private TeamAssignmentService teamAssignmentService;

    @Test
    void continuesWithNextMatchWhenOneMatchFails() {
        when(teamAssignmentService.findStartableMatchIds(NOW)).thenReturn(List.of(1L, 2L, 3L));
        when(teamAssignmentService.startMatch(2L, NOW)).thenThrow(new IllegalStateException("확정 참가자가 정원보다 많습니다."));

        new MatchStartScheduler(teamAssignmentService).startMatches(NOW);

        verify(teamAssignmentService).startMatch(1L, NOW);
        verify(teamAssignmentService).startMatch(2L, NOW);
        verify(teamAssignmentService).startMatch(3L, NOW);
    }
}
