package co.kr.spoit.report.domain;

import java.util.Optional;

/**
 * {@code report.category}에 저장되는 신고 유형이다.
 *
 * <p>화면이 제시하는 네 가지 유형만 허용한다. 알 수 없는 값은 저장하지 않고 거부해, 유형 코드가
 * 임의 문자열로 오염되지 않게 한다.</p>
 */
public enum ReportCategory {

    NO_SHOW("노쇼"),
    BAD_MANNER("비매너·욕설"),
    VIOLENCE("폭력·싸움"),
    OTHER("기타");

    private final String dbValue;

    ReportCategory(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    /** 요청 값을 해석한다. 정확히 일치하는 유형만 인정한다. */
    public static Optional<ReportCategory> from(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.strip();
        for (ReportCategory category : values()) {
            if (category.dbValue.equals(value)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
