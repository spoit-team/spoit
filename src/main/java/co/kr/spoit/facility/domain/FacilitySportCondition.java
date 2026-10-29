package co.kr.spoit.facility.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시설 종목의 모집 조건 조회 결과다.
 *
 * <p>최대 인원은 {@code facility_sport.default_max}를 그대로 쓰고, 참가 안내는
 * 이번에 추가한 컬럼이다. 기존 매칭방에는 소급 적용하지 않는다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class FacilitySportCondition {

    private Long sportId;
    private String sportName;
    private Integer capacity;
    private String note;
}
