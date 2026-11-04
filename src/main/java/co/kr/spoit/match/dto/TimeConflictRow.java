package co.kr.spoit.match.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** findTimeConflicts 조회 결과: 회원 한 명이 이미 들어가 있는, 신청하려는 매치와 시간이 겹치는 다른 매치 */
@Getter
@Setter
@NoArgsConstructor
public class TimeConflictRow {

    private Long userId;
    private String nickname;
    private Long matchId;
    private String title;
    private String facilityName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** 신청 또는 참가 상태 (대기·확정) */
    private String status;
}
