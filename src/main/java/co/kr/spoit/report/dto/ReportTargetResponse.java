package co.kr.spoit.report.dto;

/**
 * 경기 결과 화면에서 회원이 고를 수 있는 신고 대상 한 명이다.
 *
 * <p>확정 참가자 중 신고자 본인을 제외한 회원만 담는다. 화면이 대상을 고르는 데 필요한 회원 번호와
 * 닉네임 외의 정보는 내보내지 않는다.</p>
 */
public record ReportTargetResponse(Long userId, String nickname) {
}
