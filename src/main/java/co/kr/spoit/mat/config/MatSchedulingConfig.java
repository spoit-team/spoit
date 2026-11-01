package co.kr.spoit.mat.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 경기 시작 스케줄러. spoit.match-start-scheduler.enabled=false로 끌 수 있다(테스트는 build.gradle에서 끈다). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "spoit.match-start-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MatSchedulingConfig {
}
