package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 마이페이지 경기 이력 한 줄: 내 sport_rating_history 한 행과 경기·시설·신청 정보. 시설·신청 유형은 없으면 null이다. */
@Getter
@Setter
@NoArgsConstructor
public class MyRatingHistoryRow {

    private Long historyId;
    private Long sportId;
    private String title;
    private LocalDateTime startAt;
    private String sportName;
    private String facilityName;
    private String applicationType;
    private String matchSide;
    private BigDecimal mmrBefore;
    private BigDecimal mmrAfter;
    private BigDecimal eloDelta;
    private BigDecimal evaluationDelta;
    private BigDecimal finalDelta;
    private BigDecimal evaluationScore;
    private BigDecimal actualScore;
    private BigDecimal teamAvgMmrA;
    private BigDecimal teamAvgMmrB;
    private LocalDateTime calculatedAt;
}
