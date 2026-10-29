package co.kr.spoit.facility.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.facility.domain.FacilityOccupancy;
import co.kr.spoit.facility.domain.FacilitySummary;
import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.mapper.FacilityMapper;
import co.kr.spoit.facility.mapper.FacilityScheduleMapper;
import co.kr.spoit.match.domain.MatchStatus;

@Service
public class FacilityServiceImpl implements FacilityService {

    /**
     * 시설 삭제를 막는 매칭방 상태다. 취소·종료된 방은 남아 있어도 삭제를 허용한다.
     * 상태 문자열은 {@link MatchStatus}에서만 가져와 중복 정의하지 않는다.
     */
    private static final List<String> BLOCKING_DELETE_STATUSES = List.of(
            MatchStatus.RECRUITING.dbValue(), MatchStatus.MATCHED.dbValue());

    /** 동시 대관 점유로 인정하는 상태다. 구역 수 축소 검증에도 같은 기준을 쓴다. */
    private static final List<String> OCCUPYING_STATUSES = List.of(
            MatchStatus.RECRUITING.dbValue(), MatchStatus.MATCHED.dbValue());

    /** 종목 해제가 매칭방 참조로 막혔을 때의 안내 문구다. 사전 검사와 FK 충돌 변환이 함께 쓴다. */
    private static final String SPORT_REFERENCED_MESSAGE =
            "매칭방이 참조하는 종목은 해제할 수 없습니다. 해당 종목을 유지한 채 다시 시도해 주세요.";

    /**
     * 사전 검사와 실제 DELETE 사이에 다른 트랜잭션이 매칭방을 추가해 외래 키가 막는 경우를
     * 식별하는 MySQL 제약 이름이다. 이 제약의 충돌만 사용자 오류로 바꾼다.
     */
    private static final String MATCH_FACILITY_SPORT_FK = "fk_social_match_facility_sport";

    /** MySQL foreign key 위반(부모 행 참조) 오류 코드다. */
    private static final int MYSQL_FK_VIOLATION = 1451;

    private final FacilityMapper facilityMapper;
    private final FacilityScheduleMapper scheduleMapper;

    public FacilityServiceImpl(FacilityMapper facilityMapper, FacilityScheduleMapper scheduleMapper) {
        this.facilityMapper = facilityMapper;
        this.scheduleMapper = scheduleMapper;
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
        // 소유권을 먼저 확인해 권한 없는 요청이 시설 존재 여부를 추가로 알 수 없게 한다.
        requireOwnership(userId, facilityId);
        // 이후 읽기는 모두 잠금 읽기로 수행해 동시 수정의 이전 스냅샷을 쓰지 않는다.
        if (facilityMapper.selectActiveFacilityIdForUpdate(facilityId) == null) {
            throw new FacilityNotFoundException("수정할 시설을 찾을 수 없습니다.");
        }
        List<Long> sportIds = resolveSportIds(form.getSports());

        // 기존 매핑을 지우지 않고 변경분만 반영해 유지 종목의 default_* 값을 보존한다.
        List<Long> existingSportIds = facilityMapper.selectFacilitySportIds(facilityId);
        List<Long> retainedSportIds = existingSportIds == null ? List.of() : existingSportIds;

        List<Long> removedSportIds = retainedSportIds.stream()
                .filter(sportId -> !sportIds.contains(sportId))
                .toList();
        // social_match는 상태와 무관하게 facility_sport를 외래 키로 참조한다.
        // 취소·종료된 방이라도 참조 중인 종목을 해제하면 DB에서 삭제가 거부되므로 먼저 사용자 오류로 막는다.
        if (!removedSportIds.isEmpty()
                && facilityMapper.selectMatchIdForSportsForUpdate(facilityId, removedSportIds) != null) {
            throw new IllegalArgumentException(SPORT_REFERENCED_MESSAGE);
        }
        List<Long> addedSportIds = sportIds.stream()
                .filter(sportId -> !retainedSportIds.contains(sportId))
                .toList();

        // 구역 수를 줄여 기존 동시 점유 peak가 새 한도를 넘으면 시설 수정 전체(필드·종목)를 거부한다.
        requireCapacityAllowsNewLimit(facilityId, form.getConcurrentMatchLimit());

        int updated = facilityMapper.updateFacility(facilityId, form);
        if (updated == 0) {
            throw new FacilityNotFoundException("수정할 시설을 찾을 수 없습니다.");
        }
        if (!removedSportIds.isEmpty()) {
            deleteRemovedSports(facilityId, removedSportIds);
        }
        if (!addedSportIds.isEmpty()) {
            facilityMapper.insertFacilitySports(facilityId, addedSportIds);
        }
    }

    @Override
    @Transactional
    public void deleteFacility(Long userId, long facilityId) {
        // 소유권을 먼저 확인해 권한 없는 요청이 시설 존재 여부를 추가로 알 수 없게 한다.
        requireOwnership(userId, facilityId);
        if (facilityMapper.selectActiveFacilityIdForUpdate(facilityId) == null) {
            throw new FacilityNotFoundException("삭제할 시설을 찾을 수 없습니다.");
        }
        // 모집 중·매칭 완료 방이 남아 있으면 시설을 숨기지 않는다. 취소·종료 방은 삭제를 막지 않는다.
        if (facilityMapper.selectMatchIdInStatusesForUpdate(facilityId, BLOCKING_DELETE_STATUSES) != null) {
            throw new IllegalArgumentException(
                    "모집 중이거나 매칭 완료된 매칭방이 있어 시설을 삭제할 수 없습니다.");
        }
        int deleted = facilityMapper.deleteFacility(facilityId);
        if (deleted == 0) {
            throw new FacilityNotFoundException("삭제할 시설을 찾을 수 없습니다.");
        }
    }

    /**
     * 해제 대상 종목 매핑을 삭제한다.
     *
     * <p>사전 검사 뒤 다른 트랜잭션이 같은 시설·종목 매칭방을 추가하면 외래 키가 삭제를 막는다.
     * 이때 {@code fk_social_match_facility_sport} 충돌만 사용자 안내 예외로 바꾸고, 그 밖의
     * 무결성 오류는 원인을 보존한 채 그대로 전파해 트랜잭션을 롤백시킨다.</p>
     */
    private void deleteRemovedSports(long facilityId, List<Long> removedSportIds) {
        try {
            facilityMapper.deleteFacilitySportsByIds(facilityId, removedSportIds);
        } catch (DataIntegrityViolationException exception) {
            if (isMatchSportReferenceViolation(exception)) {
                throw new IllegalArgumentException(SPORT_REFERENCED_MESSAGE, exception);
            }
            throw exception;
        }
    }

    /** MySQL 1451(부모 행 참조)이면서 원인이 정확히 매칭방-시설종목 외래 키일 때만 참이다. */
    private static boolean isMatchSportReferenceViolation(DataIntegrityViolationException exception) {
        // MyBatis/Spring이 감싼 깊이 차이를 흡수하되, 원인 체인 안의 SQLException만 판단 근거로 쓴다.
        Throwable current = exception.getCause();
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                if (sqlException.getErrorCode() != MYSQL_FK_VIOLATION) {
                    return false;
                }
                String message = sqlException.getMessage();
                return message != null && message.contains(MATCH_FACILITY_SPORT_FK);
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 구역 수 축소가 기존 동시 점유와 충돌하지 않는지 확인한다.
     *
     * <p>한도는 종목과 무관하게 시설 전체에 적용하고, 각 시간 구간의 동시 점유 최대값으로
     * 판단한다. 새 한도가 지금보다 크거나 같으면 검사할 필요가 없다. 초과하면 예외를 던져
     * 호출 트랜잭션 전체(모든 시설 필드·종목 변경)를 롤백시킨다.</p>
     */
    private void requireCapacityAllowsNewLimit(long facilityId, Integer newLimit) {
        if (newLimit == null) {
            return;
        }
        Integer currentLimit = scheduleMapper.selectConcurrentLimitForUpdate(facilityId);
        if (currentLimit != null && newLimit >= currentLimit) {
            return;
        }
        List<MatchPeriod> occupying = scheduleMapper.selectMatchPeriodsForUpdate(facilityId, OCCUPYING_STATUSES);
        int peak = FacilityOccupancy.peak(occupying);
        if (peak > newLimit) {
            throw new IllegalArgumentException(
                    "현재 같은 시간에 " + peak + "개 구역이 사용 중이라 대관 가능 구역 수를 "
                            + newLimit + "개로 줄일 수 없습니다. 기존 매칭방을 정리한 뒤 다시 시도해 주세요.");
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
