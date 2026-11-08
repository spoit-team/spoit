package co.kr.spoit.mat.domain;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 마이페이지 종목 카드에 쓰는 내 sport_rating 한 행과 종목명. */
@Getter
@Setter
@NoArgsConstructor
public class MyRatingRow {

    private Long sportId;
    private String sportName;
    private BigDecimal currentMmr;
    private int ratedMatchCount;
}
