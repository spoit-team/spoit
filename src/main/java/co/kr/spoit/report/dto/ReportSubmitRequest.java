package co.kr.spoit.report.dto;

/**
 * 회원이 경기 결과 화면에서 보내는 신고 접수 요청 본문이다.
 *
 * <p>신고자 번호는 담지 않는다. 신고자는 항상 인증 주체에서만 결정한다.</p>
 */
public record ReportSubmitRequest(Long targetUserId, String category, String summary) {
}
