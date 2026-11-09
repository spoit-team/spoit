package co.kr.spoit.facility.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 종료 매칭의 시작 시간대별 확정 참가 인원 조회 결과 */
@Getter
@Setter
@NoArgsConstructor
public class HourParticipantCount {

    /** 0 ~ 23 */
    private int hour;
    /** 해당 시간대에 시작한 종료 매칭의 확정 참가자 행 수 */
    private long participantCount;
}
