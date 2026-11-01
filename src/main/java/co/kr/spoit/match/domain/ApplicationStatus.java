package co.kr.spoit.match.domain;

import java.util.List;

/**
 * match_application.status 값.
 *
 * <p>대기는 내부 신청 대기열이며, 확정 시점에만 match_participant 행이 생긴다.</p>
 */
public enum ApplicationStatus {

    WAITING("대기"),
    CONFIRMED("확정"),
    CANCELLED("취소"),
    REJECTED("반려");

    private final String dbValue;

    ApplicationStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    /** 같은 매칭방 중복 신청을 막는 상태들의 DB 값 */
    public static List<String> activeValues() {
        return List.of(WAITING.dbValue, CONFIRMED.dbValue);
    }
}
