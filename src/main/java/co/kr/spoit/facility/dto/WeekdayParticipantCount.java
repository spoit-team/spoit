package co.kr.spoit.facility.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 종료 매칭의 요일별 확정 참가 인원 조회 결과 */
@Getter
@Setter
@NoArgsConstructor
public class WeekdayParticipantCount {

    /** MySQL DAYOFWEEK: 1=일, 2=월, ... 7=토 */
    private int weekday;
    /** 해당 요일에 시작한 종료 매칭의 확정 참가자 행 수 */
    private long participantCount;
}
