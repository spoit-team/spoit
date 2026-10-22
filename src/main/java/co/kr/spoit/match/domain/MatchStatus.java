package co.kr.spoit.match.domain;

/**
 * social_match.status에 저장되는 매칭방 상태.
 *
 * <p>DB 값이 바뀌면 dbValue만 수정하면 된다. 쿼리는 이 값을 파라미터로 받는다.</p>
 */
public enum MatchStatus {

    RECRUITING("모집 중"),
    MATCHED("매칭 완료"),
    CLOSED("종료"),
    CANCELLED("취소");

    private final String dbValue;

    MatchStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
