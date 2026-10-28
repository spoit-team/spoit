package co.kr.spoit.facility.dto;

import lombok.Getter;

/**
 * 모집 조건 폼의 현재 선택 값이다.
 *
 * <p>저장된 조건이 없는 종목이면 최대 인원 기본값만 채우고 마감·안내는 비운다.</p>
 */
@Getter
public class ConditionsView {

    private final Long sportId;
    private final Integer capacity;
    private final Integer deadline;
    private final String note;

    public ConditionsView(Long sportId, Integer capacity, Integer deadline, String note) {
        this.sportId = sportId;
        this.capacity = capacity;
        this.deadline = deadline;
        this.note = note;
    }

    public static ConditionsView empty() {
        return new ConditionsView(null, null, 1, null);
    }
}
