package co.kr.spoit.member.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import co.kr.spoit.member.domain.AdminUserRow;
import co.kr.spoit.member.domain.MemberRole;
import co.kr.spoit.member.service.MemberPenaltyTimeService;

/**
 * 사용자 관리 목록 한 행의 화면 모델이다.
 *
 * <p>역할·승인·계정 상태를 화면 클래스(tone)와 라벨로 미리 계산해 템플릿이 분기 없이 출력하게 한다.
 * {@code lastActive}는 마지막 활동을 저장하는 컬럼이 없어 값이 없음을 그대로 표시한다.</p>
 */
public class AdminUserListItem {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String NONE = "—";

    private final Long userId;
    private final String nickname;
    private final String email;
    private final String roleLabel;
    private final String roleTone;
    private final String roleValue;
    private final String approvalLabel;
    private final String approvalTone;
    private final boolean pending;
    private final String statusLabel;
    private final String statusTone;
    private final boolean suspended;
    private final String sanction;
    private final String joinedAt;
    private final String lastActive;

    public AdminUserListItem(Long userId, String nickname, String email, String roleLabel, String roleTone,
            String roleValue, String approvalLabel, String approvalTone, boolean pending, String statusLabel,
            String statusTone, boolean suspended, String sanction, String joinedAt, String lastActive) {
        this.userId = userId;
        this.nickname = nickname;
        this.email = email;
        this.roleLabel = roleLabel;
        this.roleTone = roleTone;
        this.roleValue = roleValue;
        this.approvalLabel = approvalLabel;
        this.approvalTone = approvalTone;
        this.pending = pending;
        this.statusLabel = statusLabel;
        this.statusTone = statusTone;
        this.suspended = suspended;
        this.sanction = sanction;
        this.joinedAt = joinedAt;
        this.lastActive = lastActive;
    }

    /** 조회 행을 화면 모델로 바꾼다. 정지 판정은 호출자가 넘긴 현재 시각 기준이다. */
    public static AdminUserListItem from(AdminUserRow row, LocalDateTime now) {
        MemberRole role = MemberRole.from(row.getRole());
        boolean pending = Boolean.TRUE.equals(row.getPendingRequest());
        boolean suspended = row.getPenaltyTime() != null && row.getPenaltyTime().isAfter(now);
        boolean permanent = suspended && MemberPenaltyTimeService.isPermanentEndAt(row.getPenaltyTime());
        return new AdminUserListItem(
                row.getUserId(),
                nullToDash(row.getNickname()),
                nullToDash(row.getEmail()),
                role.label(),
                tone(role),
                value(role),
                pending ? "대기" : "없음",
                pending ? "warn" : "neutral",
                pending,
                suspensionLabel(suspended, permanent),
                suspended ? "bad" : "good",
                suspended,
                sanction(suspended, permanent, row.getPenaltyTime()),
                row.getCreatedAt() == null ? NONE : DATE.format(row.getCreatedAt()),
                NONE);
    }

    /** 계정 상태 라벨. 영구 정지는 종료 날짜가 없으므로 별도 문구로 보여 준다. */
    private static String suspensionLabel(boolean suspended, boolean permanent) {
        if (!suspended) {
            return "정상";
        }
        return permanent ? "영구 정지" : "정지";
    }

    /** 제재 보조 문구. 영구 정지는 센티널 날짜(9999-12-31)를 노출하지 않는다. */
    private static String sanction(boolean suspended, boolean permanent, LocalDateTime penaltyTime) {
        if (!suspended) {
            return null;
        }
        if (permanent) {
            return "영구 정지 · 복구 전까지";
        }
        return "이용 제한 ~ " + DATE_TIME.format(penaltyTime);
    }

    static String tone(MemberRole role) {
        return switch (role) {
            case ADMIN -> "system";
            case FACILITY_MANAGER -> "facility";
            case MEMBER -> "";
        };
    }

    /** 화면 select 값(member/facility/system)을 돌려준다. */
    public static String value(MemberRole role) {
        return switch (role) {
            case ADMIN -> "system";
            case FACILITY_MANAGER -> "facility";
            case MEMBER -> "member";
        };
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? NONE : value;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public String getRoleTone() {
        return roleTone;
    }

    public String getRoleValue() {
        return roleValue;
    }

    public String getApprovalLabel() {
        return approvalLabel;
    }

    public String getApprovalTone() {
        return approvalTone;
    }

    public boolean isPending() {
        return pending;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getStatusTone() {
        return statusTone;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public String getSanction() {
        return sanction;
    }

    public String getJoinedAt() {
        return joinedAt;
    }

    public String getLastActive() {
        return lastActive;
    }
}
