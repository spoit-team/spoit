package co.kr.spoit.report.service;

/**
 * 신고 대상 경기·참가자가 담당 시설·확정 참가자 조건을 만족하지 않을 때 던진다.
 *
 * <p>시설 소유권 위반과 대상 조건 위반을 구분하지 않고 같은 예외로 모아, 권한 없는 요청이
 * 다른 시설·참가자의 존재 여부를 추론하지 못하게 한다.</p>
 */
public class ReportAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReportAccessDeniedException(String message) {
        super(message);
    }
}
