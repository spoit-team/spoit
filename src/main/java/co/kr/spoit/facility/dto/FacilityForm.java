package co.kr.spoit.facility.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 시설 등록·수정 폼을 담는 값 객체다.
 *
 * <p>입력값 검증은 {@code FacilityService} 계약이 담당한다(이 저장소 컨벤션).
 * 화면에 지역·좌표 입력란이 없으므로 등록 시 지역과 좌표는 서비스 기본값으로 채우고,
 * 수정 시에는 기존 값을 유지한다.</p>
 */
@Getter
@Setter
public class FacilityForm {

    private String name;

    private String address;

    /** 이용 종목 이름 목록. 쉼표 구분 문자열로 전송되어 스프링이 분해한다. */
    private List<String> sports;

    private Integer concurrentMatchLimit;

    private String status;

    /**
     * 등록 시 매퍼가 채우는 생성 ID다. 요청 값은 저장에 쓰이지 않으며
     * {@code useGeneratedKeys} 결과만 신뢰한다.
     */
    private Long facilityId;

    @Override
    public String toString() {
        return "FacilityForm{name='" + name + "', address='" + address + "', sports=" + sports
                + ", concurrentMatchLimit=" + concurrentMatchLimit + ", status='" + status + "'}";
    }
}
