package co.kr.spoit.mat.scheduler;

import co.kr.spoit.mat.service.TeamAssignmentService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MatchStartScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final TeamAssignmentService teamAssignmentService;

    public MatchStartScheduler(TeamAssignmentService teamAssignmentService) {
        this.teamAssignmentService = teamAssignmentService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void run() {
        startMatches(LocalDateTime.now(ZONE));
    }

    // 여기에는 트랜잭션을 걸지 않는다. startMatch 호출 하나가 경기 하나의 트랜잭션이라
    // 한 경기가 실패해 롤백돼도 앞서 처리한 경기는 유지되고 다음 경기를 계속 처리한다.
    void startMatches(LocalDateTime now) {
        List<Long> matchIds = teamAssignmentService.findStartableMatchIds(now);
        for (Long matchId : matchIds) {
            try {
                teamAssignmentService.startMatch(matchId, now);
            } catch (RuntimeException e) {
                log.error("경기 시작 처리 실패: matchId={}", matchId, e);
            }
        }
    }
}
