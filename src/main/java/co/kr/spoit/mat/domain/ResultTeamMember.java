package co.kr.spoit.mat.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 경기 결과 제출 화면의 확정 참가자. userId는 팀장 확인에만 쓰고 화면에는 내보내지 않는다. */
@Getter
@Setter
@NoArgsConstructor
public class ResultTeamMember {

    private Long participantId;
    private Long userId;
    private String nickname;
    private String matchSide;
}
