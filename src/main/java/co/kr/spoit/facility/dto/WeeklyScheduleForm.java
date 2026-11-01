package co.kr.spoit.facility.dto;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 요일별 운영 시간 저장 요청이다.
 *
 * <p>화면 폼 계약은 {@code facilityId, openDays(1~7), start-<day>, end-<day>}이다.
 * 휴무 행은 {@code disabled}라 시작·종료 값이 전송되지 않으므로, {@code openDays}에 없는
 * 요일은 휴무로 저장한다. 7일 전체 휴무도 허용한다.</p>
 */
@Getter
@Setter
public class WeeklyScheduleForm {

    /** 저장 대상 시설이다. 폼 계약의 {@code facilityId}와 같다. */
    private Long facilityId;

    /** 운영하는 요일(1=월 ~ 7=일). 비어 있으면 7일 전체 휴무다. */
    private List<Integer> openDays = List.of();

    /** 요일별 시작 시각 원문("HH:mm"). 24:00은 시작 시각으로 쓸 수 없다. */
    private Map<Integer, String> startTimes = Map.of();

    /** 요일별 종료 시각 원문("HH:mm"). 24:00은 다음 날 00:00 종료다. */
    private Map<Integer, String> endTimes = Map.of();

    public boolean isOpen(int weekday) {
        return openDays != null && openDays.contains(weekday);
    }
}
