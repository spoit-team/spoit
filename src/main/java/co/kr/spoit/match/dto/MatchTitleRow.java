package co.kr.spoit.match.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 탈퇴 차단 사유 조회 결과: 매치 식별자와 제목. 같은 제목의 다른 매치를 구분하기 위해 id를 함께 읽는다. */
@Getter
@Setter
@NoArgsConstructor
public class MatchTitleRow {

    private Long matchId;
    private String title;
}
