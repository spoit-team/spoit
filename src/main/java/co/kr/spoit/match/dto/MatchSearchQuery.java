package co.kr.spoit.match.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** MatchMapper 검색 파라미터 */
@Getter
@Builder
public class MatchSearchQuery {

    /** start_at 검색 구간 [from, to) */
    private final LocalDateTime from;
    private final LocalDateTime to;
    private final List<String> visibleStatuses;
    private final Long sportId;
    private final Long regionId;
    /** 선택한 시간대(시 단위). 하나라도 걸치는 방을 찾는다. 비어 있으면 시간 조건 없음 */
    private final List<Integer> hours;
    private final String recruitingStatus;
    private final String matchedStatus;
}
