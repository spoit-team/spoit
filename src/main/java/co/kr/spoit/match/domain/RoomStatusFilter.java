package co.kr.spoit.match.domain;

import java.util.Locale;

/**
 * 시설 관리자 매칭방 목록의 방 상태 필터다.
 *
 * <p>화면 파라미터 값과 DB {@code social_match.status} 값을 함께 관리한다. 알 수 없는 값은
 * {@link #ALL}로 되돌려 필터 선택 상태와 조회 조건이 어긋나지 않게 한다.</p>
 */
public enum RoomStatusFilter {

    ALL("all", null),
    RECRUITING("recruiting", MatchStatus.RECRUITING.dbValue()),
    MATCHED("matched", MatchStatus.MATCHED.dbValue()),
    /** 화면 문구는 '이용 완료'이고 DB 값은 종료 상태다. */
    COMPLETED("completed", MatchStatus.CLOSED.dbValue()),
    CANCELLED("cancelled", MatchStatus.CANCELLED.dbValue());

    private final String value;
    private final String dbValue;

    RoomStatusFilter(String value, String dbValue) {
        this.value = value;
        this.dbValue = dbValue;
    }

    /** 화면·쿼리스트링에 쓰는 값 */
    public String value() {
        return value;
    }

    /** 조회 조건으로 넘길 DB 값, '전체'면 null */
    public String dbValue() {
        return dbValue;
    }

    /** null·공백·알 수 없는 값은 전체로 정규화한다. */
    public static RoomStatusFilter from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT);
        for (RoomStatusFilter filter : values()) {
            if (filter.value.equals(normalized)) {
                return filter;
            }
        }
        return ALL;
    }
}
