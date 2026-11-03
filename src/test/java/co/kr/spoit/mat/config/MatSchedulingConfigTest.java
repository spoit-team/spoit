package co.kr.spoit.mat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import co.kr.spoit.mat.scheduler.MatchResultScheduler;
import co.kr.spoit.mat.scheduler.MatchStartScheduler;
import co.kr.spoit.mat.service.MatchResultService;
import co.kr.spoit.mat.service.TeamAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

/** 스케줄러 프로퍼티 하나로 경기 시작·종료·결과 마감 스케줄러 빈이 함께 켜지고 꺼지는지 검증한다. */
class MatSchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MatSchedulingConfig.class)
            .withBean(TeamAssignmentService.class, () -> mock(TeamAssignmentService.class))
            .withBean(MatchResultService.class, () -> mock(MatchResultService.class));

    @Test
    void registersBothSchedulersWhenPropertyMissing() {
        // Gradle 테스트 설정이 시스템 프로퍼티로 끄고 있으므로, 이 컨텍스트에서만 시스템 프로퍼티를 제외해 기본값을 본다.
        runner.withInitializer(context -> context.getEnvironment().getPropertySources()
                        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME))
                .run(context -> {
                    assertThat(context).hasSingleBean(MatchStartScheduler.class);
                    assertThat(context).hasSingleBean(MatchResultScheduler.class);
                });
    }

    @Test
    void registersBothSchedulersWhenPropertyTrue() {
        runner.withPropertyValues("spoit.match-start-scheduler.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MatchStartScheduler.class);
                    assertThat(context).hasSingleBean(MatchResultScheduler.class);
                });
    }

    @Test
    void registersNoSchedulerWhenPropertyFalse() {
        runner.withPropertyValues("spoit.match-start-scheduler.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MatchStartScheduler.class);
                    assertThat(context).doesNotHaveBean(MatchResultScheduler.class);
                });
    }
}
