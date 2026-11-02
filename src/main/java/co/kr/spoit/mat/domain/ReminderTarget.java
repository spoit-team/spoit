package co.kr.spoit.mat.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 결과 제출 리마인더를 받을 팀장. */
@Getter
@Setter
@NoArgsConstructor
public class ReminderTarget {

    private Long matchId;
    private Long userId;
}
