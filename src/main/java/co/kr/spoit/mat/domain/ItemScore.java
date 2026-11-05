package co.kr.spoit.mat.domain;

import java.math.BigDecimal;

/** 검증을 마친 항목 하나의 점수. NOT_OBSERVED면 score는 null이다. */
public record ItemScore(Long itemId, BigDecimal weight, ObservationStatus observationStatus, Integer score) {
}
