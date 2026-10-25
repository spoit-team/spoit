package co.kr.spoit.match.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** countBySport 조회 결과 */
@Getter
@Setter
@NoArgsConstructor
public class SportCount {

    private Long sportId;
    private long roomCount;
}
