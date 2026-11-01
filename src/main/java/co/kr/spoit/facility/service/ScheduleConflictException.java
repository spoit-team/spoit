package co.kr.spoit.facility.service;

import java.util.List;

import co.kr.spoit.facility.domain.MatchPeriod;

/**
 * 변경하려는 일정이 모집 중·매칭 완료 매칭방과 충돌해 저장하지 않았을 때 던진다.
 *
 * <p>충돌한 방을 <b>전부</b> 담아 화면 경고에 그대로 전달한다. 기존 매칭방이나 신청을
 * 자동 취소하지 않는다.</p>
 */
public class ScheduleConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<MatchPeriod> conflicts;
    private final String rangeText;

    public ScheduleConflictException(String message, List<MatchPeriod> conflicts, String rangeText) {
        super(message);
        this.conflicts = List.copyOf(conflicts);
        this.rangeText = rangeText;
    }

    /** 충돌한 매칭방 전체다. */
    public List<MatchPeriod> getConflicts() {
        return conflicts;
    }

    /** 경고에 함께 보여 줄 입력 구간 텍스트다. */
    public String getRangeText() {
        return rangeText;
    }
}
