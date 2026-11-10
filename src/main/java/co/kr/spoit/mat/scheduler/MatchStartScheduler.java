package co.kr.spoit.mat.scheduler;

import co.kr.spoit.mat.service.TeamAssignmentService;
import co.kr.spoit.match.service.MatchApplicationService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** 빈 등록 여부는 {@code mat.config.MatSchedulingConfig}가 프로퍼티로 결정한다. */
@Slf4j
public class MatchStartScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final TeamAssignmentService teamAssignmentService;
    private final MatchApplicationService applicationService;

    public MatchStartScheduler(TeamAssignmentService teamAssignmentService,
                               MatchApplicationService applicationService) {
        this.teamAssignmentService = teamAssignmentService;
        this.applicationService = applicationService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        startMatches(now);
        rejectUnconfirmed(now);
    }

    /**
     * 시작 시각이 지나도 확정되지 않은 결제 대기 신청을 반려하고 참가비를 환불한다.
     * 관리자가 미리 팀을 배정한 경기도 포함한다. 매칭방마다 한 트랜잭션이다.
     */
    void rejectUnconfirmed(LocalDateTime now) {
        List<Long> matchIds;
        try {
            matchIds = applicationService.findStartedMatchIdsWithWaiting(now);
        } catch (RuntimeException e) {
            log.error("미확정 신청 조회 실패", e);
            return;
        }
        for (Long matchId : matchIds) {
            try {
                int rejected = applicationService.rejectUnconfirmed(matchId, now);
                if (rejected > 0) {
                    log.info("경기 시작 전 미확정 신청 반려: matchId={}, 건수={}", matchId, rejected);
                }
            } catch (RuntimeException e) {
                log.error("미확정 신청 반려 실패: matchId={}", matchId, e);
            }
        }
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
