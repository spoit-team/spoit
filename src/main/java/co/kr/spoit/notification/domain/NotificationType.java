package co.kr.spoit.notification.domain;

/**
 * notification.notification_type 값과 화면 표시 문구.
 *
 * <p>DB에는 상수 이름 그대로 저장한다. 알 수 없는 코드는 저장된 문자열을 그대로 표시하므로
 * 다른 도메인이 새 코드를 추가해도 읽기 경로가 깨지지 않는다.</p>
 */
public enum NotificationType {

    MATCH_RESULT_CONFLICT("경기 결과 확인"),
    MATCH_RESULT_REMINDER("경기 결과 제출"),
    MATCH_AUTO_ASSIGNED("팀 배정 완료"),
    MATCH_AUTO_CANCELLED("경기 자동 취소"),
    MATCH_RESULT_COMPLETED("경기 결과 산정 완료"),
    MATCH_APPLICATION_RECEIVED("매칭 신청 접수"),
    MATCH_APPLICATION_CONFIRMED("매칭 신청 확정"),
    MATCH_APPLICATION_CANCELLED("매칭 신청 취소"),
    MATCH_ROOM_CANCELLED("매칭방 취소"),
    CLUB_JOIN_REQUESTED("가입 신청 접수"),
    CLUB_JOIN_APPROVED("가입 승인"),
    CLUB_JOIN_REJECTED("가입 거절"),
    CLUB_MEMBER_KICKED("팀원 추방"),
    CLUB_LEADER_TRANSFERRED("회장 위임"),
    REPORT_SUBMITTED("신고 접수"),
    REPORT_RESOLVED("신고 처리 결과");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 저장된 코드 문자열에 대응하는 화면 문구를 돌려준다.
     * 정의되지 않은 코드는 원문을 그대로 사용하고, null·공백은 빈 문자열로 바꾼다.
     */
    public static String labelOf(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "";
        }
        for (NotificationType type : values()) {
            if (type.name().equals(rawType)) {
                return type.label;
            }
        }
        return rawType;
    }
}
