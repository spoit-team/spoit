package co.kr.spoit.match.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 매칭방 검색 요청 파라미터 (sportId, regionId, date, hours).
 *
 * <p>모델 "search"로 그대로 쓰인다. 템플릿은 날짜를 {@code search.dateIdx}로 읽고,
 * 폼은 {@code date}로 보낸다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class MatchSearchCondition {

    private Long sportId;
    private Long regionId;
    /** 오늘 기준 날짜 인덱스 (0 = 오늘), null이면 전체 날짜 */
    private Integer dateIdx;
    /** 선택한 시간대 ("09:00" 형식) */
    private List<String> hours = new ArrayList<>();

    /** 요청 파라미터 date 바인딩용 */
    public void setDate(Integer date) {
        this.dateIdx = date;
    }
}
