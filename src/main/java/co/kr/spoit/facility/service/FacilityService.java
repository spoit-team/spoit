package co.kr.spoit.facility.service;

import java.util.List;

import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.dto.FacilityListItem;

/**
 * 시설 관리자 화면의 담당 시설 조회·변경을 담당한다.
 *
 * <p>모든 변경은 인증 주체가 담당자로 매핑된 시설에만 허용된다.</p>
 */
public interface FacilityService {

    /** 로그인한 시설 관리자가 담당하는 시설 목록을 조회한다. */
    List<FacilityListItem> findManagedFacilities(Long userId);

    /** 시설을 등록하고 담당자 매핑·종목 매핑까지 저장한다. 등록된 시설 ID를 반환한다. */
    long registerFacility(Long userId, FacilityForm form);

    /** 담당 시설의 기본 정보와 종목을 수정한다. 지역·좌표는 기존 값을 유지한다. */
    void updateFacility(Long userId, long facilityId, FacilityForm form);

    /** 담당 시설을 논리 삭제한다(담당자·종목 매핑은 유지). */
    void deleteFacility(Long userId, long facilityId);
}
