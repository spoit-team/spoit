package co.kr.spoit.facility.domain;

import lombok.Getter;
import lombok.Setter;

/** 시설에 속한 날짜별 예외 한 시간 슬롯의 조회 결과다. */
@Getter
@Setter
public class DateOverrideSlot {
    private Long overrideId;
    private Integer slotHour;
}
