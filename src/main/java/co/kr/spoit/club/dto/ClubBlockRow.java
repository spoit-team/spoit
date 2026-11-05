package co.kr.spoit.club.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 탈퇴 차단 사유 조회 결과: 동호회 식별자와 이름. 같은 이름의 다른 동호회를 구분하기 위해 id를 함께 읽는다. */
@Getter
@Setter
@NoArgsConstructor
public class ClubBlockRow {

    private Long clubId;
    private String clubName;
}
