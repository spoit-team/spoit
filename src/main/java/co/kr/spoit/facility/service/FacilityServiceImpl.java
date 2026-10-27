package co.kr.spoit.facility.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.facility.domain.FacilitySummary;
import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.mapper.FacilityMapper;

@Service
public class FacilityServiceImpl implements FacilityService {

    private final FacilityMapper facilityMapper;

    public FacilityServiceImpl(FacilityMapper facilityMapper) {
        this.facilityMapper = facilityMapper;
    }

    @Override
    @Transactional(readOnly = true)
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

    @Override
    @Transactional
    public long registerFacility(Long userId, FacilityForm form) {
        validate(form);
        List<Long> sportIds = resolveSportIds(form.getSports());
        facilityMapper.insertFacility(form);
        Long generatedFacilityId = form.getFacilityId();
        if (generatedFacilityId == null) {
            throw new IllegalStateException("시설 등록 후 생성 ID를 받지 못했습니다.");
        }
        long facilityId = generatedFacilityId;
        facilityMapper.insertFacilityManager(facilityId, userId);
        facilityMapper.insertFacilitySports(facilityId, sportIds);
        return facilityId;
    }

    @Override
    @Transactional
    public void updateFacility(Long userId, long facilityId, FacilityForm form) {
        validate(form);
        requireOwnership(userId, facilityId);
        List<Long> sportIds = resolveSportIds(form.getSports());
        int updated = facilityMapper.updateFacility(facilityId, form);
        if (updated == 0) {
            throw new FacilityNotFoundException("수정할 시설을 찾을 수 없습니다.");
        }
        facilityMapper.deleteFacilitySports(facilityId);
        facilityMapper.insertFacilitySports(facilityId, sportIds);
    }

    @Override
    @Transactional
    public void deleteFacility(Long userId, long facilityId) {
        requireOwnership(userId, facilityId);
        int deleted = facilityMapper.deleteFacility(facilityId);
        if (deleted == 0) {
            throw new FacilityNotFoundException("삭제할 시설을 찾을 수 없습니다.");
        }
    }

    /** 화면 폼 계약을 검증한다. 오류 메시지는 그대로 안내 문구로 쓴다. */
    private void validate(FacilityForm form) {
        if (form == null || isBlank(form.getName()) || form.getName().strip().length() > 100) {
            throw new IllegalArgumentException("시설명을 1~100자로 입력해 주세요.");
        }
        if (isBlank(form.getAddress()) || form.getAddress().strip().length() > 255) {
            throw new IllegalArgumentException("시설 주소를 1~255자로 입력해 주세요.");
        }
        if (form.getConcurrentMatchLimit() == null || form.getConcurrentMatchLimit() < 1
                || form.getConcurrentMatchLimit() > 100) {
            throw new IllegalArgumentException("대관 가능 구역 수는 1~100 사이로 입력해 주세요.");
        }
        if (isBlank(form.getStatus()) || form.getStatus().strip().length() > 30) {
            throw new IllegalArgumentException("운영 상태를 선택해 주세요.");
        }
    }

    private void requireOwnership(Long userId, long facilityId) {
        if (userId == null || !facilityMapper.existsFacilityManager(facilityId, userId)) {
            throw new FacilityAccessDeniedException("담당 시설만 관리할 수 있습니다.");
        }
    }

    private List<Long> resolveSportIds(List<String> sports) {
        List<String> names = sports == null ? List.of()
                : sports.stream().filter(name -> name != null && !name.isBlank()).map(String::strip).distinct().toList();
        if (names.isEmpty()) {
            throw new IllegalArgumentException("이용 종목을 하나 이상 선택해 주세요.");
        }
        List<Long> sportIds = facilityMapper.findSportIdsByNames(names);
        if (sportIds == null || sportIds.size() != names.size()) {
            throw new IllegalArgumentException("선택한 이용 종목을 찾을 수 없습니다.");
        }
        return sportIds;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
