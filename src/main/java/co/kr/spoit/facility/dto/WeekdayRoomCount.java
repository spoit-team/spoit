package co.kr.spoit.facility.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 요일별 매칭방 수 조회 결과 */
@Getter
@Setter
@NoArgsConstructor
public class WeekdayRoomCount {

    /** MySQL DAYOFWEEK: 1=일, 2=월, ... 7=토 */
    private int weekday;
    private long roomCount;
}
