package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import co.kr.spoit.mat.domain.MyRatingHistoryRow;
import co.kr.spoit.mat.domain.MyRatingRow;
import co.kr.spoit.mat.dto.MyRatingRecords;
import co.kr.spoit.mat.service.MyRatingRecordService;

@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
// 공용 fixture 행은 쓰지 않아 전용 fixture만 지정한다. 여기 @Sql이 상위 클래스의 @Sql을 대신한다.
@Sql(scripts = "/mat/my-rating-record-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class MyRatingRecordMapperIntegrationTest extends AbstractMySqlMapperTest {

    private static final long ME = 911L;
    private static final long SPORT_A = 911L;
    private static final long SPORT_B = 912L;

    @Autowired
    private MyRatingRecordMapper recordMapper;

    @Test
    void historyIsLatestTwentyOfMineByCalculatedAt() {
        List<MyRatingHistoryRow> history = recordMapper.findHistory(ME, null, 20);

        // 반영 23건 중 최근 20건: 종목 A n=22..9(9278~9291), 종목 B(9500), 종목 A n=8..4(9292~9296).
        // ID 순과는 반대이고, 오래된 3건(9297~9299)과 다른 회원(912)의 이력(9401~9422)은 없다.
        assertThat(history).extracting(MyRatingHistoryRow::getHistoryId).containsExactly(
                9278L, 9279L, 9280L, 9281L, 9282L, 9283L, 9284L, 9285L, 9286L, 9287L,
                9288L, 9289L, 9290L, 9291L, 9500L, 9292L, 9293L, 9294L, 9295L, 9296L);
        assertThat(history).extracting(MyRatingHistoryRow::getCalculatedAt)
                .isSortedAccordingTo(Comparator.<LocalDateTime>reverseOrder());
        assertThat(history).extracting(MyRatingHistoryRow::getTitle).doesNotContain("종목 A 제외 경기");
    }

    @Test
    void sportFilterKeepsOnlyThatSportAndJoinsDisplayValues() {
        List<MyRatingHistoryRow> history = recordMapper.findHistory(ME, SPORT_B, 20);

        assertThat(history).hasSize(1);
        MyRatingHistoryRow row = history.get(0);
        assertThat(row.getSportId()).isEqualTo(SPORT_B);
        assertThat(row.getTitle()).isEqualTo("종목 B 동호회 경기");
        assertThat(row.getStartAt()).isEqualTo(LocalDateTime.of(2026, 8, 9, 18, 0));
        assertThat(row.getSportName()).isEqualTo("레이팅 이력 테스트 종목 B");
        assertThat(row.getFacilityName()).isEqualTo("레이팅 이력 테스트 시설");
        // 본인 신청 행이 없는 동호회 신청 팀원도 참가자 행의 신청(대표 913의 동호회 신청)으로 유형을 찾는다.
        assertThat(row.getApplicationType()).isEqualTo("동호회");
        assertThat(row.getMatchSide()).isEqualTo("B");
        assertThat(row.getMmrBefore()).isEqualByComparingTo("1032.40");
        assertThat(row.getMmrAfter()).isEqualByComparingTo("1016.40");
        assertThat(row.getEloDelta()).isEqualByComparingTo("-16.00");
        assertThat(row.getEvaluationDelta()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.getFinalDelta()).isEqualByComparingTo("-16.00");
        assertThat(row.getEvaluationScore()).isNull();
        assertThat(row.getActualScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.getTeamAvgMmrA()).isEqualByComparingTo("1010.25");
        assertThat(row.getTeamAvgMmrB()).isEqualByComparingTo("990.75");
    }

    @Test
    void clubMemberHistoryIsShownAsTeamApplication() {
        MyRatingRecords records = new MyRatingRecordService(recordMapper).find(ME, String.valueOf(SPORT_B));

        assertThat(records.getHistory())
                .extracting(MyRatingRecords.HistoryItem::getTitle, MyRatingRecords.HistoryItem::getApplicationType)
                .containsExactly(tuple("종목 B 동호회 경기", "팀 신청"));
    }

    @Test
    void countsMyHistoryForAllSportsAndOneSport() {
        assertThat(recordMapper.countHistory(ME, null)).isEqualTo(23);
        assertThat(recordMapper.countHistory(ME, SPORT_A)).isEqualTo(22);
        assertThat(recordMapper.countHistory(ME, SPORT_B)).isEqualTo(1);
    }

    @Test
    void ratingsAreMineWithRatedMatchesInSportOrder() {
        // 0경기 행(종목 C)과 다른 회원(912)의 종목 A 행은 빠진다.
        assertThat(recordMapper.findRatings(ME))
                .extracting(MyRatingRow::getSportId, MyRatingRow::getSportName, MyRatingRow::getRatedMatchCount)
                .containsExactly(
                        tuple(SPORT_A, "레이팅 이력 테스트 종목 A", 22),
                        tuple(SPORT_B, "레이팅 이력 테스트 종목 B", 1));
        assertThat(recordMapper.findRatings(ME).get(0).getCurrentMmr()).isEqualByComparingTo("1363.00");
    }
}
