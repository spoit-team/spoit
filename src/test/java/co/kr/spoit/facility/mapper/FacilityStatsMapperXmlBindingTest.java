package co.kr.spoit.facility.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 예약 통계 매퍼 XML 문이 인터페이스와 실제로 바인딩되는지 확인한다. */
@SpringBootTest
class FacilityStatsMapperXmlBindingTest {

    private static final String NAMESPACE = "co.kr.spoit.facility.mapper.FacilityStatsMapper.";

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("FacilityStatsMapper XML 문이 인터페이스 네임스페이스에 바인딩된다")
    void xmlStatementsAreBoundToInterface() {
        var configuration = sqlSessionFactory.getConfiguration();

        assertThat(configuration.hasMapper(FacilityStatsMapper.class)).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "countFinishedMatches")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "countConfirmedParticipants")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "countApplications")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + "countParticipantsByWeekdayAndHour")).isTrue();
    }
}
