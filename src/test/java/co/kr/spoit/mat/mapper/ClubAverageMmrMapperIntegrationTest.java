package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.club.dto.ClubSearchForm;
import co.kr.spoit.club.mapper.ClubMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
// 공용 fixture 행은 쓰지 않아 전용 fixture만 지정한다. 여기 @Sql이 상위 클래스의 @Sql을 대신한다.
@Sql(scripts = "/mat/club-average-mmr-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class ClubAverageMmrMapperIntegrationTest extends AbstractMySqlMapperTest {

    @Autowired
    private ClubMapper clubMapper;

    @Test
    void averagesOnlyMembersWhoFinishedPlacement() {
        // 5경기 회원(1100)만 들어가고 4경기 회원(700)은 빠진다.
        assertThat(clubMapper.selectClubDetail(901L).getAverageMmr()).isEqualTo(1100);
    }

    @Test
    void levelFilterUsesSamePlacementAverage() {
        // 실버 구간은 1000 이상 1200 미만이다. 예전 기준 평균(900)이었다면 결과에서 빠진다.
        ClubSearchForm silver = new ClubSearchForm();
        silver.setSportId(901L);
        silver.setLevel(2);

        assertThat(clubMapper.selectClubs(silver))
                .extracting(ClubSummary::getClubId, ClubSummary::getAverageMmr)
                .containsExactly(tuple(901L, 1100));
    }
}
