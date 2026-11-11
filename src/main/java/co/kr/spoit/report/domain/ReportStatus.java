package co.kr.spoit.report.domain;

import java.util.Optional;

/**
 * {@code report.status}에 저장되는 신고 처리 상태다.
 *
 * <p>값은 {@code 접수 → 검토 중 → 처리 완료 또는 기각} 네 가지로 고정한다. 알 수 없는 값은
 * 임의로 변환하지 않고 {@link Optional#empty()}로 돌려주며, 화면·서비스는 그런 레코드를
 * 읽기 전용으로만 다룬다. mat 이력 조회가 기대하는 {@code 처리 완료} 표기와도 같다.</p>
 */
public enum ReportStatus {

    RECEIVED("접수"),
    REVIEWING("검토 중"),
    COMPLETED("처리 완료"),
    DISMISSED("기각");

    private final String dbValue;

    ReportStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    /** 저장 값을 해석한다. 정확히 일치하는 상태만 인정하고 나머지는 알 수 없는 값으로 둔다. */
    public static Optional<ReportStatus> from(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.strip();
        for (ReportStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    /** 접수 상태만 검토 중으로 옮길 수 있다. */
    public boolean canReview() {
        return this == RECEIVED;
    }

    /** 접수·검토 중 상태만 처리 완료 또는 기각으로 마무리할 수 있다. 이미 끝난 건은 재처리하지 않는다. */
    public boolean canFinish() {
        return this == RECEIVED || this == REVIEWING;
    }
}
