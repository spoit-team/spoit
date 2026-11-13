package co.kr.spoit.report.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 신고 화면의 경기 선택지 조회 결과다. 담당 활성 시설의 이용 완료 경기만 담는다. */
@Getter
@Setter
public class ReportMatchRow {

    private Long matchId;
    private String facilityName;
    private String title;
    private LocalDateTime startAt;
}
