package co.kr.spoit.facility.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import co.kr.spoit.facility.domain.FacilityClosure;
import lombok.Getter;

/** 등록된 휴관 목록의 한 행이다. */
@Getter
public class ClosureRow {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Long scheduleChangeId;
    private final String dateLabel;
    private final String facilityName;
    private final String timeRange;
    private final String reason;
    private final String statusLabel;
    private final String statusTone;

    private ClosureRow(Long scheduleChangeId, String dateLabel, String facilityName, String timeRange,
            String reason, String statusLabel, String statusTone) {
        this.scheduleChangeId = scheduleChangeId;
        this.dateLabel = dateLabel;
        this.facilityName = facilityName;
        this.timeRange = timeRange;
        this.reason = reason;
        this.statusLabel = statusLabel;
        this.statusTone = statusTone;
    }

    public static ClosureRow from(FacilityClosure closure, String facilityName) {
        LocalDateTime start = closure.getStartAt();
        LocalDateTime end = closure.getEndAt();
        String date = "";
        if (start != null) {
            date = start.format(DATE);
        }
        String range = "";
        if (start != null && end != null) {
            range = start.format(TIME) + " ~ " + end.format(TIME);
        }
        boolean active = closure.isActive();
        String statusLabel;
        String statusTone;
        if (active) {
            statusLabel = "적용 중";
            statusTone = "warn";
        } else {
            statusLabel = "취소";
            statusTone = "neutral";
        }
        return new ClosureRow(closure.getScheduleChangeId(), date, facilityName, range,
                closure.getReason(), statusLabel, statusTone);
    }
}
