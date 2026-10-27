package co.kr.spoit.facility.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.facility.domain.FacilitySummary;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.mapper.FacilityMapper;

@Service
@Transactional(readOnly = true)
public class FacilityServiceImpl implements FacilityService {

    private final FacilityMapper facilityMapper;

    public FacilityServiceImpl(FacilityMapper facilityMapper) {
        this.facilityMapper = facilityMapper;
    }

    @Override
    public List<FacilityListItem> findManagedFacilities(Long userId) {
        // 인증 주체가 없거나 ID가 비어 있으면 다른 시설을 조회하지 않고 빈 목록을 돌려준다.
        if (userId == null) {
            return List.of();
        }
        List<FacilitySummary> facilities = facilityMapper.selectManagedFacilities(userId);
        if (facilities == null || facilities.isEmpty()) {
            return List.of();
        }

        List<FacilityListItem> items = new ArrayList<>(facilities.size());
        for (FacilitySummary facility : facilities) {
            if (facility != null) {
                items.add(FacilityListItem.from(facility));
            }
        }
        return List.copyOf(items);
    }
}
