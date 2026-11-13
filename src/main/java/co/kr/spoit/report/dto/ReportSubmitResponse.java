package co.kr.spoit.report.dto;

/**
 * 회원 신고 접수 API의 응답 본문이다.
 *
 * <p>성공하면 생성되었거나 중복으로 판정된 신고 번호를 담는다. 실패 응답에서는 {@code reportId}가
 * {@code null}이고 안내 문구만 담는다. 신고 원문·대상 정보는 응답에 싣지 않는다.</p>
 */
public record ReportSubmitResponse(Long reportId, String message) {
}
