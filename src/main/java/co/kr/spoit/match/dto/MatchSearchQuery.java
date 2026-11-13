package co.kr.spoit.match.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** MatchMapper 검색 파라미터 */
@Getter
@Builder(toBuilder = true)
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
    /** 무한 스크롤 커서: 이 (start_at, match_id) 다음 행부터. 첫 페이지·집계면 null */
    private final LocalDateTime cursorStartAt;
    private final Long cursorMatchId;
    /** 가져올 최대 행 수. null이면 제한 없음 */
    private final Integer limit;
}
