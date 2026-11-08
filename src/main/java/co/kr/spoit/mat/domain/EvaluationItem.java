package co.kr.spoit.mat.domain;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 경기 종목의 활성 평가 항목. weight는 평가자별 점수 계산에만 쓰고 화면에는 내보내지 않는다. */
@Getter
@Setter
@NoArgsConstructor
public class EvaluationItem {

    private Long itemId;
    private String itemName;
    private BigDecimal weight;
}
