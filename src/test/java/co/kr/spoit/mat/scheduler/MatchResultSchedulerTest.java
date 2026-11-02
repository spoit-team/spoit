package co.kr.spoit.mat.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.service.MatchResultService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchResultSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 20, 0);

    @Mock
    private MatchResultService matchResultService;

    @Test
    void continuesWithOtherWorkWhenOneStepOrMatchFails() {
        doThrow(new IllegalStateException("조회 실패")).when(matchResultService).sendReminders(NOW);
        when(matchResultService.findEndedMatchIds(NOW)).thenReturn(List.of(1L, 2L));
        when(matchResultService.markEnded(1L, NOW)).thenThrow(new IllegalStateException("잠금 대기 시간 초과"));
        when(matchResultService.findExpiredMatchIds(NOW)).thenReturn(List.of(3L, 4L));
        when(matchResultService.closeResult(3L, NOW)).thenThrow(new IllegalStateException("레이팅 반영 실패"));

        new MatchResultScheduler(matchResultService).process(NOW);

        verify(matchResultService).markEnded(2L, NOW);
        verify(matchResultService).closeResult(4L, NOW);
    }
}
