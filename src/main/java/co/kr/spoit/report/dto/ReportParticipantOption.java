package co.kr.spoit.report.dto;

import lombok.Getter;

/** 신고 대상 선택지다. 선택한 경기의 확정 참가자만 담는다. */
@Getter
public class ReportParticipantOption {

    private final Long userId;
    private final String nickname;

    public ReportParticipantOption(Long userId, String nickname) {
        this.userId = userId;
        this.nickname = nickname;
    }
}
