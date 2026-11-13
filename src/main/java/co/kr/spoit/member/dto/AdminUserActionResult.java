package co.kr.spoit.member.dto;

/**
 * 사용자 관리의 상태 변경 결과다. 화면에 그대로 보여 줄 한국어 메시지를 담는다.
 *
 * <p>대기 중인 {@code admin_request}가 없거나 이미 시스템 관리자인 계정을 건드리려 하면
 * 서비스가 예외 대신 이 결과로 안전하게 거절한다.</p>
 */
public record AdminUserActionResult(boolean applied, String message) {

    public static AdminUserActionResult applied(String message) {
        return new AdminUserActionResult(true, message);
    }

    public static AdminUserActionResult rejected(String message) {
        return new AdminUserActionResult(false, message);
    }
}
