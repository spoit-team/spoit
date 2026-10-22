package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 회원과 종목별로 저장된 내부 MMR을 나타낸다. */
@Getter
@Setter
@NoArgsConstructor
public class SportRating {

    private Long userId;
    private Long sportId;
    private BigDecimal currentMmr;
    private Integer ratedMatchCount;
    private LocalDateTime updatedAt;
}
