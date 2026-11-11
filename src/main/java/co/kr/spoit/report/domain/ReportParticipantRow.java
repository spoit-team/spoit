package co.kr.spoit.report.domain;

import lombok.Getter;
import lombok.Setter;

/** 신고 대상 선택지 조회 결과다. 확정 참가자만 담긴다. */
@Getter
@Setter
public class ReportParticipantRow {

    private Long userId;
    private String nickname;
}
