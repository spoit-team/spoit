package co.kr.spoit.match.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 참가자 아바타·목록 표시용 */
@Getter
@AllArgsConstructor
public class ParticipantView {

    private final String nickname;
    private final String initial;
    /** 방장 여부 */
    private final boolean host;
}
