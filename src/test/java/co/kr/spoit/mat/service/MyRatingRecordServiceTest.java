package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.domain.MyRatingHistoryRow;
import co.kr.spoit.mat.domain.MyRatingRow;
import co.kr.spoit.mat.dto.MyRatingRecords.Fact;
import co.kr.spoit.mat.dto.MyRatingRecords.HistoryItem;
import co.kr.spoit.mat.dto.MyRatingRecords.TierText;
import co.kr.spoit.mat.mapper.MyRatingRecordMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyRatingRecordServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    private MyRatingRecordMapper recordMapper;

    @InjectMocks
    private MyRatingRecordService service;

    @Test
    void movesWithinDivisionAsCumulativePointDifference() {
        HistoryItem item = only(history("1015.50", "1046.50", "1.00", 9));

        assertThat(item.getDeltaText()).isEqualTo("+62P");
        assertThat(item.getDeltaKey()).isEqualTo("up");
        assertThat(change(item)).isEqualTo("실버 4 · 31P → 실버 4 · 93P");
        assertThat(item.getDivisionChange()).isNull();
        assertThat(item.getFacts()).extracting(Fact::getLabel, Fact::getValue)
                .containsExactly(
                        tuple("승패", "+60P"),
                        tuple("평가", "+2P"),
                        tuple("평가 점수", "6.00점"));
        assertThat(item.getAverageA()).extracting(TierText::getText, TierText::getTierKey).containsExactly("실버 4", "silver");
        assertThat(item.getAverageB()).extracting(TierText::getText, TierText::getTierKey).containsExactly("브론즈 1", "bronze");
    }

    @Test
    void marksPromotion() {
        HistoryItem item = only(history("943.61", "960.00", "0.84", 15));

        assertThat(item.getDeltaText()).isEqualTo("+33P");
        assertThat(change(item)).isEqualTo("브론즈 2 · 87P → 브론즈 1 · 20P");
        assertThat(item.getDivisionChange()).isEqualTo("승급");
        assertThat(pointFacts(item)).containsExactly("+31P", "+2P");
    }

    @Test
    void marksDemotion() {
        HistoryItem item = only(history("1003.00", "990.00", "-0.25", 7));

        assertThat(item.getDeltaText()).isEqualTo("-26P");
        assertThat(item.getDeltaKey()).isEqualTo("down");
        assertThat(change(item)).isEqualTo("실버 4 · 6P → 브론즈 1 · 80P");
        // 화살표 앞뒤는 각자 그 티어 색이다.
        assertThat(tierKeys(item)).containsExactly("silver", "bronze");
        assertThat(item.getDivisionChange()).isEqualTo("강등");
        assertThat(pointFacts(item)).containsExactly("-25P", "-1P");
    }

    @Test
    void staysAtZeroPointsBelowTierStart() {
        HistoryItem item = only(history("790.00", "785.00", "0.50", 8));

        assertThat(item.getDeltaText()).isEqualTo("0P");
        assertThat(item.getDeltaKey()).isEqualTo("zero");
        assertThat(change(item)).isEqualTo("브론즈 4 · 0P → 브론즈 4 · 0P");
        assertThat(item.getDivisionChange()).isNull();
        // 총 변동 0P 안에서 평가 몫이 +1P면 승패 몫은 -1P다.
        assertThat(pointFacts(item)).containsExactly("-1P", "+1P");
    }

    @ParameterizedTest
    @CsvSource({"1, 배치 1/5, 배치 경기 1/5", "4, 배치 4/5, 배치 경기 4/5"})
    void placementMatchHidesTierAndPoints(int countAfter, String delta, String expectedChange) {
        HistoryItem item = only(history("1000.00", "968.25", "-0.66", countAfter));

        assertThat(item.getDeltaText()).isEqualTo(delta);
        assertThat(item.getDeltaKey()).isEqualTo("placement");
        assertThat(change(item)).isEqualTo(expectedChange);
        assertThat(item.getRatingFrom().getTierKey()).isNull();
        assertThat(item.getRatingTo()).isNull();
        assertThat(item.getDivisionChange()).isNull();
        assertThat(item.getFacts()).extracting(Fact::getLabel).containsExactly("평가 점수");
    }

    @Test
    void placementCompletingMatchShowsOnlyResultingTier() {
        HistoryItem item = only(history("1015.50", "1046.50", "1.00", 5));

        assertThat(item.getDeltaText()).isEqualTo("배치 완료");
        assertThat(item.getDeltaKey()).isEqualTo("placement");
        assertThat(change(item)).isEqualTo("배치 완료 → 실버 4 · 93P");
        // "배치 완료"는 중립색, 화살표 뒤만 티어 색이다.
        assertThat(tierKeys(item)).containsExactly(null, "silver");
        assertThat(item.getDivisionChange()).isNull();
        assertThat(item.getFacts()).extracting(Fact::getLabel).containsExactly("평가 점수");
    }

    @Test
    void firstMatchAfterPlacementShowsPoints() {
        HistoryItem item = only(history("1015.50", "1046.50", "1.00", 6));

        assertThat(item.getDeltaText()).isEqualTo("+62P");
        assertThat(item.getFacts()).extracting(Fact::getLabel).containsExactly("승패", "평가", "평가 점수");
    }

    @Test
    void resultAndEvaluationPointsAlwaysAddUpToTotal() {
        String[] befores = {"799.99", "849.99", "999.75", "1000.00", "1015.50", "1199.99", "1795.12"};
        String[] finals = {"-31.75", "-16.25", "-0.01", "0.00", "0.24", "0.25", "16.39", "35.05"};
        String[] evaluations = {"-0.66", "-0.25", "0.00", "0.25", "0.84", "3.33", "4.75"};
        for (String before : befores) {
            for (String finalDelta : finals) {
                for (String evaluation : evaluations) {
                    BigDecimal after = new BigDecimal(before).add(new BigDecimal(finalDelta));
                    HistoryItem item = only(history(before, after.toPlainString(), evaluation, 10));
                    List<String> facts = pointFacts(item);

                    assertThat(points(facts.get(0)) + points(facts.get(1)))
                            .as("%s %s %s", before, finalDelta, evaluation)
                            .isEqualTo(points(item.getDeltaText()));
                }
            }
        }
    }

    @Test
    void historyNeverShowsMmr() {
        HistoryItem item = only(history("1015.50", "1046.50", "1.00", 9));

        assertThat(List.of(item.getDeltaText(), change(item), item.getAverageA().getText(), item.getAverageB().getText()))
                .noneMatch(text -> text.contains("MMR"));
        assertThat(item.getFacts()).noneMatch(fact -> fact.getLabel().contains("MMR") || fact.getValue().contains("MMR"));
    }

    private HistoryItem only(MyRatingHistoryRow row) {
        MyRatingRow rating = new MyRatingRow();
        rating.setSportId(2L);
        rating.setSportName("풋살");
        rating.setCurrentMmr(row.getMmrAfter());
        rating.setRatedMatchCount(row.getRatedMatchCountAfter());
        when(recordMapper.findRatings(USER_ID)).thenReturn(List.of(rating));
        when(recordMapper.countHistory(USER_ID, null)).thenReturn(1);
        when(recordMapper.findHistory(USER_ID, null, 20)).thenReturn(List.of(row));
        return service.find(USER_ID, null).getHistory().get(0);
    }

    /** 화면처럼 화살표 앞뒤를 이어 붙인 문구 */
    private static String change(HistoryItem item) {
        return item.getRatingTo() == null ? item.getRatingFrom().getText()
                : item.getRatingFrom().getText() + " → " + item.getRatingTo().getText();
    }

    private static List<String> tierKeys(HistoryItem item) {
        return Arrays.asList(item.getRatingFrom().getTierKey(), item.getRatingTo().getTierKey());
    }

    private static List<String> pointFacts(HistoryItem item) {
        return item.getFacts().stream()
                .filter(fact -> fact.getLabel().equals("승패") || fact.getLabel().equals("평가"))
                .map(Fact::getValue)
                .toList();
    }

    private static int points(String text) {
        return Integer.parseInt(text.replace("P", "").replace("+", ""));
    }

    private static MyRatingHistoryRow history(String mmrBefore, String mmrAfter, String evaluationDelta, int countAfter) {
        BigDecimal before = new BigDecimal(mmrBefore);
        BigDecimal after = new BigDecimal(mmrAfter);
        BigDecimal evaluation = new BigDecimal(evaluationDelta);
        var row = new MyRatingHistoryRow();
        row.setHistoryId(1L);
        row.setSportId(2L);
        row.setTitle("주말 풋살");
        row.setStartAt(LocalDateTime.of(2026, 10, 3, 10, 0));
        row.setSportName("풋살");
        row.setFacilityName("테스트 체육관");
        row.setApplicationType("개인");
        row.setMatchSide("A");
        row.setMmrBefore(before);
        row.setMmrAfter(after);
        row.setFinalDelta(after.subtract(before));
        row.setEvaluationDelta(evaluation);
        row.setEloDelta(after.subtract(before).subtract(evaluation));
        row.setEvaluationScore(new BigDecimal("5").add(evaluation));
        row.setActualScore(BigDecimal.ONE);
        row.setTeamAvgMmrA(new BigDecimal("1010.25"));
        row.setTeamAvgMmrB(new BigDecimal("990.75"));
        row.setCalculatedAt(LocalDateTime.of(2026, 10, 4, 12, 0));
        row.setRatedMatchCountAfter(countAfter);
        return row;
    }
}
