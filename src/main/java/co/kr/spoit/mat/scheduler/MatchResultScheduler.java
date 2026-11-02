package co.kr.spoit.mat.scheduler;

import co.kr.spoit.mat.service.MatchResultService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MatchResultScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MatchResultService matchResultService;

    public MatchResultScheduler(MatchResultService matchResultService) {
        this.matchResultService = matchResultService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void run() {
        process(LocalDateTime.now(ZONE));
    }

    // MatchStartScheduler처럼 여기에는 트랜잭션을 걸지 않아 경기마다 따로 커밋·롤백한다.
    // 리마인더는 이번 1분 구간을 놓치면 다시 보내지 않으므로 먼저 보내고, 종료 전환과 마감은 다음 실행에서 다시 찾는다.
    void process(LocalDateTime now) {
        try {
            matchResultService.sendReminders(now);
        } catch (RuntimeException e) {
            log.error("결과 제출 리마인더 실패: now={}", now, e);
        }
        forEachMatch(matchResultService.findEndedMatchIds(now), "경기 종료 전환",
                matchId -> matchResultService.markEnded(matchId, now));
        forEachMatch(matchResultService.findExpiredMatchIds(now), "결과 마감 처리",
                matchId -> matchResultService.closeResult(matchId, now));
    }

    private void forEachMatch(List<Long> matchIds, String task, Consumer<Long> action) {
        for (Long matchId : matchIds) {
            try {
                action.accept(matchId);
            } catch (RuntimeException e) {
                log.error("{} 실패: matchId={}", task, matchId, e);
            }
        }
    }
}
