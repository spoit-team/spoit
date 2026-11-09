package co.kr.spoit.report.domain;

import java.time.LocalDateTime;

/** 신고 화면의 경기 선택지 조회 결과다. 담당 활성 시설의 이용 완료 경기만 담는다. */
public class ReportMatchRow {

    private Long matchId;
    private String facilityName;
    private String title;
    private LocalDateTime startAt;

    public Long getMatchId() {
        return matchId;
    }

    public void setMatchId(Long matchId) {
        this.matchId = matchId;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }
}
