package co.kr.spoit.match.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** countMatches 조회 결과 */
@Getter
@Setter
@NoArgsConstructor
public class MatchCountSummary {

    private long roomCount;
    private long recruitingCount;
    private long matchedCount;
}
