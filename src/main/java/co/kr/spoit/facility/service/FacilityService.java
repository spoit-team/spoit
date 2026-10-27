package co.kr.spoit.facility.service;

import java.util.List;

import co.kr.spoit.facility.dto.FacilityListItem;

public interface FacilityService {

    /** 로그인한 시설 관리자가 담당하는 시설 목록을 조회한다. */
    List<FacilityListItem> findManagedFacilities(Long userId);
}
