package co.kr.spoit.report.domain;

import java.util.Optional;

/**
 * 시스템 관리자의 신고 처리 동작이다.
 *
 * <p>허용 전이는 상태 enum이 판정하고, 이 타입은 요청 문자열을 동작으로만 해석한다.</p>
 */
public enum ReportDecisionAction {

    /** 접수 → 검토 중 */
    REVIEW("review", ReportStatus.REVIEWING),
    /** 접수/검토 중 → 처리 완료 */
    COMPLETE("complete", ReportStatus.COMPLETED),
    /** 접수/검토 중 → 기각 */
    DISMISS("dismiss", ReportStatus.DISMISSED);

    private final String requestValue;
    private final ReportStatus targetStatus;

    ReportDecisionAction(String requestValue, ReportStatus targetStatus) {
        this.requestValue = requestValue;
        this.targetStatus = targetStatus;
    }

    public String requestValue() {
        return requestValue;
    }

    public ReportStatus targetStatus() {
        return targetStatus;
    }

    /** 최종 처리(처리 완료·기각)인지 여부. 최종 처리에서만 신고자에게 결과를 알린다. */
    public boolean isFinal() {
        return this != REVIEW;
    }

    /** 요청 값을 해석한다. 알 수 없는 동작은 거부한다. */
    public static Optional<ReportDecisionAction> from(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.strip();
        for (ReportDecisionAction action : values()) {
            if (action.requestValue.equals(value)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
