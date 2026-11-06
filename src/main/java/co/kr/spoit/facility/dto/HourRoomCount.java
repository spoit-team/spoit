package co.kr.spoit.facility.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 시간대별 매칭방 수 조회 결과 */
@Getter
@Setter
@NoArgsConstructor
public class HourRoomCount {

    /** 0 ~ 23 */
    private int hour;
    private long roomCount;
}
