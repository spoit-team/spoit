package co.kr.spoit.report.domain;

/** 신고 대상 선택지 조회 결과다. 확정 참가자만 담긴다. */
public class ReportParticipantRow {

    private Long userId;
    private String nickname;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
