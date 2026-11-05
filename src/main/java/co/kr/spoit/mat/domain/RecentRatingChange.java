package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 참가자 이력에 보여 줄 sport_rating_history 한 건의 반영 시각과 최종 증감. */
@Getter
@Setter
@NoArgsConstructor
public class RecentRatingChange {

    private LocalDateTime calculatedAt;
    private BigDecimal finalDelta;
}
