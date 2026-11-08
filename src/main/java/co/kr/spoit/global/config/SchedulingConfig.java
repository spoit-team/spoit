package co.kr.spoit.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션 전역 스케줄링 인프라를 켠다.
 *
 * <p>스케줄 작업의 실행 여부는 각 작업이 속한 기능의 조건으로 판단한다. 예를 들어 경기 시작
 * 스케줄러는 {@code spoit.match-start-scheduler.enabled} 조건으로 빈 등록 자체가 제어된다.
 * 이 설정은 그 토글과 무관하게 항상 적용되어, 시스템 모니터링 수집처럼 다른 기능의 주기 작업이
 * 매치 스케줄러 토글에 종속되지 않게 한다.</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
