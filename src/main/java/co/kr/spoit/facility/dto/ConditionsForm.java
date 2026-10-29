package co.kr.spoit.facility.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 종목별 모집 조건 저장 요청이다.
 *
 * <p>화면 폼 계약은 {@code facilityId, sportId, capacity, note}이다. 최대 인원은
 * {@code facility_sport.default_max}에 저장하고 2~200만 허용한다. 조건은 신규 예약에만
 * 적용하며 기존 매칭방에 소급하지 않는다.</p>
 */
@Getter
@Setter
public class ConditionsForm {

    private Long facilityId;

    private Long sportId;

    private Integer capacity;

    private String note;
}
