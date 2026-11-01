package co.kr.spoit.facility.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시설에서 점유 중인 매칭방의 시간 구간이다.
 *
 * <p>모집 중·매칭 완료 상태만 담는다. 취소·종료된 방은 점유 계산과 일정 충돌 검사에서
 * 제외한다. {@code scheduleConflicts} 경고 목록에도 이 형태로 전달한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class MatchPeriod {

    private Long matchId;
    private String title;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    /** 경고창에 표시할 "시작 ~ 종료" 문자열이다. 값이 없으면 빈 문자열이다. */
    public String getPeriodText() {
        if (startAt == null || endAt == null) {
            return "";
        }
        return startAt.toLocalDate() + " " + startAt.toLocalTime() + " ~ " + endAt.toLocalTime();
    }
}
