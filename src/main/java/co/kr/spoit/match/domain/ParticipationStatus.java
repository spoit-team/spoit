package co.kr.spoit.match.domain;

import java.util.List;

/**
 * match_participant.participation_status 값.
 *
 * <p>정원 계산에는 WAITING, CONFIRMED만 포함한다.
 * (DDL의 active_match_user 생성 컬럼과 같은 기준)</p>
 */
public enum ParticipationStatus {

    WAITING("대기"),
    CONFIRMED("확정"),
    CANCELLED("취소"),
    REJECTED("반려");

    private final String dbValue;

    ParticipationStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    /** 정원을 차지하는 상태들의 DB 값 */
    public static List<String> occupyingValues() {
        return List.of(WAITING.dbValue, CONFIRMED.dbValue);
    }
}
