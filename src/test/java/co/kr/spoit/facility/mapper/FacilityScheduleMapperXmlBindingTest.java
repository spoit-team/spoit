package co.kr.spoit.facility.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 예약 운영 일정 매퍼가 인터페이스와 바인딩되고, 최신 값을 다시 읽는 잠금 조회가
 * MyBatis 1차 캐시(세션 캐시)를 우회하는지 확인한다.
 *
 * <p>세션 캐시가 켜져 있으면 같은 트랜잭션에서 같은 문을 두 번 부르면 DB에 다시 묻지 않고
 * 이전 결과를 돌려준다. {@code FOR UPDATE} 잠금 조회는 최신 커밋 값을 읽어야 하므로
 * {@code flushCache="true"}로 캐시를 비워야 한다.</p>
 */
@SpringBootTest
class FacilityScheduleMapperXmlBindingTest {

    private static final String NAMESPACE = "co.kr.spoit.facility.mapper.FacilityScheduleMapper.";

    /** 잠금 current read여야 하는 문이다. */
    private static final List<String> LOCKING_STATEMENTS = List.of(
            "selectWeeklyRulesForUpdate",
            "selectDateOverridesForUpdate",
            "selectDateOverrideHoursForUpdate",
            "selectActiveClosuresForUpdate",
            "selectClosureForUpdate",
            "selectMatchPeriodsForUpdate",
            "selectOverlappingMatchPeriodsForUpdate",
            "selectSportCapacityForUpdate",
            "selectConcurrentLimitForUpdate",
            "selectFacilityNameForUpdate");

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("FacilityScheduleMapper XML 문이 인터페이스 네임스페이스에 바인딩된다")
    void xmlStatementsAreBoundToInterface() {
        var configuration = sqlSessionFactory.getConfiguration();

        assertThat(configuration.hasMapper(FacilityScheduleMapper.class)).isTrue();
        for (String statement : LOCKING_STATEMENTS) {
            assertThat(configuration.hasStatement(NAMESPACE + statement))
                    .as(statement)
                    .isTrue();
        }
        assertThat(configuration.hasStatement(NAMESPACE + "selectWeeklyRules")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "selectSportConditions")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "insertWeeklyRules")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "insertDateOverrideHours")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "insertClosure")).isTrue();
    }

    @Test
    @DisplayName("잠금 조회는 세션 캐시를 비워 최신 값을 다시 읽는다")
    void lockingStatementsFlushSessionCache() {
        var configuration = sqlSessionFactory.getConfiguration();

        for (String statement : LOCKING_STATEMENTS) {
            assertThat(configuration.getMappedStatement(NAMESPACE + statement).isFlushCacheRequired())
                    .as(statement)
                    .isTrue();
        }
        // 화면 조회는 캐시를 비울 필요가 없다.
        assertThat(configuration.getMappedStatement(NAMESPACE + "selectWeeklyRules").isFlushCacheRequired())
                .isFalse();
    }
}
