package co.kr.spoit.report.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.Getter;

/** 신고 화면의 경기 선택지다. 이용 완료(종료) 상태 경기만 담는다. */
@Getter
public class ReportMatchOption {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long matchId;
    private final String facilityName;
    private final String title;
    private final String startAt;

    public ReportMatchOption(Long matchId, String facilityName, String title, LocalDateTime startAt) {
        this.matchId = matchId;
        this.facilityName = facilityName;
        this.title = title;
        this.startAt = startAt == null ? "—" : DATE_TIME.format(startAt);
    }
}
