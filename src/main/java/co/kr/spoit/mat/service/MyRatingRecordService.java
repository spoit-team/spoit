package co.kr.spoit.mat.service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.mat.domain.MyRatingHistoryRow;
import co.kr.spoit.mat.domain.MyRatingRow;
import co.kr.spoit.mat.domain.RatingPolicy;
import co.kr.spoit.mat.domain.RatingTier;
import co.kr.spoit.mat.domain.TierRank;
import co.kr.spoit.mat.dto.MyRatingRecords;
import co.kr.spoit.mat.dto.MyRatingRecords.Fact;
import co.kr.spoit.mat.dto.MyRatingRecords.HistoryItem;
import co.kr.spoit.mat.dto.MyRatingRecords.SportCard;
import co.kr.spoit.mat.dto.MyRatingRecords.TierText;
import co.kr.spoit.mat.mapper.MyRatingRecordMapper;
import co.kr.spoit.match.domain.ApplicationType;

/** 마이페이지의 종목별 레이팅과 레이팅이 반영된 경기 이력. 조회만 하며 레이팅을 계산하거나 저장하지 않는다. */
@Service
@Transactional(readOnly = true)
public class MyRatingRecordService {

    private static final int HISTORY_LIMIT = 20;

    private static final String ALL_SPORTS = "전체 종목";
    private static final String EMPTY_NOTE = "레이팅이 반영된 경기가 없습니다.";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final BigDecimal DRAW_SCORE = new BigDecimal("0.5");

    private final MyRatingRecordMapper recordMapper;

    public MyRatingRecordService(MyRatingRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    /** recordSport는 화면의 종목 필터 쿼리 값 그대로다. */
    public MyRatingRecords find(Long userId, String recordSport) {
        if (userId == null || userId <= 0) {
            return new MyRatingRecords(List.of(), true, 0, ALL_SPORTS, 0, List.of(), EMPTY_NOTE);
        }
        List<MyRatingRow> ratings = recordMapper.findRatings(userId);
        MyRatingRow selected = selectedSport(ratings, recordSport);
        Long sportId = selected == null ? null : selected.getSportId();
        int totalCount = recordMapper.countHistory(userId, null);
        int count = sportId == null ? totalCount : recordMapper.countHistory(userId, sportId);
        String scope = selected == null ? ALL_SPORTS : selected.getSportName();
        return new MyRatingRecords(
                ratings.stream().map(rating -> sportCard(rating, rating == selected)).toList(),
                selected == null,
                totalCount,
                count > HISTORY_LIMIT ? "%s · 최근 %d경기만 표시".formatted(scope, HISTORY_LIMIT) : scope,
                count,
                recordMapper.findHistory(userId, sportId, HISTORY_LIMIT).stream()
                        .map(MyRatingRecordService::historyItem)
                        .toList(),
                EMPTY_NOTE);
    }

    /** 프로필 수정 화면처럼 경기 이력 없이 종목 카드만 필요할 때 쓴다. */
    public List<SportCard> findSportCards(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return recordMapper.findRatings(userId).stream().map(rating -> sportCard(rating, false)).toList();
    }

    // 숫자가 아니거나 내 레이팅이 없는 종목이면 전체로 본다.
    private static MyRatingRow selectedSport(List<MyRatingRow> ratings, String recordSport) {
        if (recordSport == null || recordSport.isBlank()) {
            return null;
        }
        long sportId;
        try {
            sportId = Long.parseLong(recordSport.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
        return ratings.stream()
                .filter(rating -> rating.getSportId() == sportId)
                .findFirst()
                .orElse(null);
    }

    private static SportCard sportCard(MyRatingRow rating, boolean selected) {
        int count = rating.getRatedMatchCount();
        boolean placed = count >= RatingPolicy.PLACEMENT_MATCHES;
        // 다이아 1은 단계를 더 올리지 않고 포인트만 쌓여 한 단계 포인트를 넘을 수 있다.
        int points = placed
                ? Math.min(TierRank.of(rating.getCurrentMmr()).points(), RatingPolicy.TIER_POINTS_PER_DIVISION)
                : 0;
        return new SportCard(rating.getSportId(), rating.getSportName(),
                RatingTier.labelOf(rating.getCurrentMmr(), count),
                placed ? TierRank.of(rating.getCurrentMmr()).tier().cssKey() : null,
                count, placed, points, selected);
    }

    private static HistoryItem historyItem(MyRatingHistoryRow row) {
        Outcome outcome = Outcome.of(row.getActualScore());
        RatingChange change = RatingChange.of(row);
        return new HistoryItem(
                row.getHistoryId(),
                row.getStartAt().format(DATE_FORMAT),
                row.getTitle(),
                row.getSportName(),
                row.getFacilityName() == null ? "시설 정보 없음" : row.getFacilityName(),
                applicationTypeLabel(row.getApplicationType()),
                outcome.label,
                outcome.key,
                change.deltaText,
                change.deltaKey,
                change.ratingFrom,
                change.ratingTo,
                change.divisionChange,
                row.getMatchSide(),
                divisionText(TierRank.of(row.getTeamAvgMmrA())),
                divisionText(TierRank.of(row.getTeamAvgMmrB())),
                change.facts(evaluationScoreFact(row.getEvaluationScore())));
    }

    private static TierText divisionText(TierRank rank) {
        return new TierText(rank.divisionLabel(), rank.tier().cssKey());
    }

    private static TierText labelText(TierRank rank) {
        return new TierText(rank.label(), rank.tier().cssKey());
    }

    private static Fact evaluationScoreFact(BigDecimal evaluationScore) {
        return new Fact("평가 점수", evaluationScore == null ? "없음"
                : evaluationScore.setScale(RatingPolicy.EVALUATION_SCORE_SCALE, RatingPolicy.ROUNDING)
                        .toPlainString() + "점");
    }

    // 같은 화면의 신청 내역(MyReservationService)과 같은 문구를 쓴다.
    private static String applicationTypeLabel(String applicationType) {
        if (applicationType == null) {
            return "신청 정보 없음";
        }
        return ApplicationType.INDIVIDUAL.dbValue().equals(applicationType) ? "개인 신청" : "팀 신청";
    }

    /**
     * 이력 한 줄의 레이팅 변화. 배치 경기는 마이페이지 종목 카드처럼 티어와 포인트를 감추고 진행 경기 수만 보여 준다.
     * 배치를 마친 경기는 화살표 뒤에 결과 티어만 보인다. 평가 점수는 배치 경기에도 보인다.
     */
    private record RatingChange(String deltaText, String deltaKey, TierText ratingFrom, TierText ratingTo,
                                String divisionChange, List<Fact> pointFacts) {

        private static final String PLACEMENT_KEY = "placement";

        static RatingChange of(MyRatingHistoryRow row) {
            int countAfter = row.getRatedMatchCountAfter();
            TierRank after = TierRank.of(row.getMmrAfter());
            if (countAfter < RatingPolicy.PLACEMENT_MATCHES) {
                return new RatingChange("배치 %d/%d".formatted(countAfter, RatingPolicy.PLACEMENT_MATCHES), PLACEMENT_KEY,
                        new TierText("배치 경기 %d/%d".formatted(countAfter, RatingPolicy.PLACEMENT_MATCHES), null), null,
                        null, List.of());
            }
            if (countAfter == RatingPolicy.PLACEMENT_MATCHES) {
                return new RatingChange("배치 완료", PLACEMENT_KEY, new TierText("배치 완료", null), labelText(after), null,
                        List.of());
            }
            TierRank before = TierRank.of(row.getMmrBefore());
            int total = after.totalPoints() - before.totalPoints();
            // 평가 몫만 반올림하고 승패 몫은 나머지로 둬, 두 값의 합이 표시된 총 변동과 항상 같다.
            int evaluation = TierRank.roundedPoints(row.getEvaluationDelta());
            return new RatingChange(signedPoints(total), deltaKey(total),
                    labelText(before), labelText(after), divisionChange(before, after),
                    List.of(new Fact("승패", signedPoints(total - evaluation)), new Fact("평가", signedPoints(evaluation))));
        }

        List<Fact> facts(Fact evaluationScore) {
            List<Fact> facts = new ArrayList<>(pointFacts);
            facts.add(evaluationScore);
            return facts;
        }

        private static String divisionChange(TierRank before, TierRank after) {
            int compared = Integer.compare(after.stepIndex(), before.stepIndex());
            if (compared == 0) {
                return null;
            }
            return compared > 0 ? "승급" : "강등";
        }

        private static String signedPoints(int points) {
            return (points > 0 ? "+" : "") + points + "P";
        }

        private static String deltaKey(int points) {
            return switch (Integer.signum(points)) {
                case 1 -> "up";
                case -1 -> "down";
                default -> "zero";
            };
        }
    }

    // actual_score에는 승 1, 무 0.5, 패 0만 저장된다.
    private enum Outcome {
        WIN("승", "win"),
        DRAW("무", "draw"),
        LOSS("패", "loss");

        private final String label;
        private final String key;

        Outcome(String label, String key) {
            this.label = label;
            this.key = key;
        }

        static Outcome of(BigDecimal actualScore) {
            int compared = actualScore.compareTo(DRAW_SCORE);
            if (compared > 0) {
                return WIN;
            }
            return compared == 0 ? DRAW : LOSS;
        }
    }
}
