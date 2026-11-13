package co.kr.spoit.report.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 제재 이력 한 행의 화면 모델이다.
 *
 * <p>{@code active}는 조기 해제 버튼 노출 여부다. 기간이 지난 {@code 적용 중} 행은 만료로
 * 표시하고 버튼을 숨긴다.</p>
 */
public class PenaltyListItem {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String NONE = "—";

    private final Long penaltyId;
    private final Long targetUserId;
    private final String account;
    private final String email;
    private final String action;
    private final String startAt;
    private final String endAt;
    private final Long reportId;
    private final String reason;
    private final String statusLabel;
    private final String statusTone;
    private final boolean active;

    public PenaltyListItem(Long penaltyId, Long targetUserId, String account, String email, String action,
            String startAt, String endAt, Long reportId, String reason, String statusLabel,
            String statusTone, boolean active) {
        this.penaltyId = penaltyId;
        this.targetUserId = targetUserId;
        this.account = account;
        this.email = email;
        this.action = action;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reportId = reportId;
        this.reason = reason;
        this.statusLabel = statusLabel;
        this.statusTone = statusTone;
        this.active = active;
    }

    /** 화면 표시용 시각. 값이 없으면 {@code "—"}. */
    public static String format(LocalDateTime value) {
        return value == null ? NONE : DATE_TIME.format(value);
    }

    public Long getPenaltyId() {
        return penaltyId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public String getAccount() {
        return account;
    }

    public String getEmail() {
        return email;
    }

    public String getAction() {
        return action;
    }

    public String getStartAt() {
        return startAt;
    }

    public String getEndAt() {
        return endAt;
    }

    public Long getReportId() {
        return reportId;
    }

    public String getReason() {
        return reason;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getStatusTone() {
        return statusTone;
    }

    public boolean isActive() {
        return active;
    }
}
