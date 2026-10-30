package co.kr.spoit.facility.service;

import java.util.List;

import co.kr.spoit.facility.domain.MatchPeriod;

/**
 * 모집 중·매칭 완료 매칭방이 남아 시설을 삭제하지 않았을 때 던진다.
 *
 * <p>삭제를 막는 방을 <b>전부</b> 담아 화면 경고에 그대로 전달한다. 매칭방을 자동으로
 * 취소하거나 시설을 숨기지 않는다.</p>
 */
public class FacilityDeleteBlockedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<MatchPeriod> conflicts;

    public FacilityDeleteBlockedException(String message, List<MatchPeriod> conflicts) {
        super(message);
        this.conflicts = List.copyOf(conflicts);
    }

    /** 삭제를 막는 매칭방 전체다. */
    public List<MatchPeriod> getConflicts() {
        return conflicts;
    }
}
