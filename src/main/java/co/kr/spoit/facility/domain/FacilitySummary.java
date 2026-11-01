package co.kr.spoit.facility.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 담당 시설 목록 조회 SQL의 결과를 담는 도메인 모델이다.
 *
 * <p>{@code facility} 행과 담당자 매핑, 시설 종목 이름을 한 번에 조회한 결과를 표현한다.
 * 수용 인원에 대응하는 컬럼은 스키마에 없으므로 이 모델에도 두지 않는다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class FacilitySummary {

    private Long facilityId;
    private String name;
    private String address;
    private String sportsLabel;
    private Integer concurrentMatchLimit;
    private String status;
}
