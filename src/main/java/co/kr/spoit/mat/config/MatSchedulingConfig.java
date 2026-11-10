package co.kr.spoit.mat.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.kr.spoit.mat.scheduler.MatchResultScheduler;
import co.kr.spoit.mat.scheduler.MatchStartScheduler;
import co.kr.spoit.mat.service.MatchResultService;
import co.kr.spoit.mat.service.TeamAssignmentService;
import co.kr.spoit.match.service.MatchApplicationService;

/**
 * 경기 시작·종료·결과 마감 스케줄러.
 *
 * <p>{@code @EnableScheduling}은 전역 {@code global.config.SchedulingConfig}가 담당한다. 이 설정은
 * {@code spoit.match-start-scheduler.enabled=false}일 때 경기 시작·종료·결과 마감 스케줄러 빈을 등록하지 않아,
 * 스케줄링 인프라가 켜져 있어도 해당 작업이 실행되지 않게 한다(테스트는 build.gradle에서 끈다).</p>
 */
@Configuration
@ConditionalOnProperty(name = "spoit.match-start-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MatSchedulingConfig {

    @Bean
    public MatchStartScheduler matchStartScheduler(TeamAssignmentService teamAssignmentService,
                                                   MatchApplicationService applicationService) {
        return new MatchStartScheduler(teamAssignmentService, applicationService);
    }

    @Bean
    public MatchResultScheduler matchResultScheduler(MatchResultService matchResultService) {
        return new MatchResultScheduler(matchResultService);
    }
}
