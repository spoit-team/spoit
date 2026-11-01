package co.kr.spoit.facility.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.facility.domain.FacilitySummary;
import co.kr.spoit.facility.dto.FacilityForm;

@Mapper
public interface FacilityMapper {

    /**
     * 지정한 회원이 담당자로 매핑된 삭제되지 않은 시설만 조회한다.
     * 시설별 종목은 한 행으로 집계해 시설이 중복되지 않게 한다.
     */
    List<FacilitySummary> selectManagedFacilities(@Param("userId") Long userId);

    /** 소유권 검증용: 해당 시설의 담당자인지 확인한다. */
    boolean existsFacilityManager(@Param("facilityId") Long facilityId, @Param("userId") Long userId);

    /** 종목 이름으로 종목 ID를 조회한다. 없는 이름은 결과에 포함되지 않는다. */
    List<Long> findSportIdsByNames(@Param("names") List<String> names);

    /** 시설을 등록하고 생성된 ID를 {@code form.facilityId}에 채운다. 저장한 행 수를 반환한다. */
    int insertFacility(@Param("form") FacilityForm form);

    /** 등록한 회원을 담당자로 매핑한다. */
    int insertFacilityManager(@Param("facilityId") long facilityId, @Param("userId") Long userId);

    /** 시설 종목 매핑을 등록한다. */
    int insertFacilitySports(@Param("facilityId") long facilityId, @Param("sportIds") List<Long> sportIds);

    /** 소유한 시설의 기본 정보를 수정한다. 지역·좌표는 기존 값을 유지한다. */
    int updateFacility(@Param("facilityId") Long facilityId, @Param("form") FacilityForm form);

    /** 시설에 등록된 종목 ID를 조회한다. 수정 시 변경분을 계산하는 데 쓴다(현재 값을 읽도록 행 잠금). */
    List<Long> selectFacilitySportIds(@Param("facilityId") long facilityId);

    /**
     * 수정·삭제 대상 활성 시설 행을 잠그고 ID를 반환한다.
     * 행이 없거나 이미 논리 삭제됐으면 {@code null}이다.
     */
    Long selectActiveFacilityIdForUpdate(@Param("facilityId") long facilityId);

    /** 지정한 상태의 매칭방 하나를 잠그고 ID를 반환한다. 없으면 {@code null}이다. */
    Long selectMatchIdInStatusesForUpdate(@Param("facilityId") long facilityId,
            @Param("statuses") List<String> statuses);

    /**
     * 지정한 시설·종목 조합을 상태와 무관하게 참조하는 매칭방 하나를 잠그고 ID를 반환한다.
     * 없으면 {@code null}이다.
     */
    Long selectMatchIdForSportsForUpdate(@Param("facilityId") long facilityId,
            @Param("sportIds") List<Long> sportIds);

    /** 지정한 종목 매핑만 삭제한다. 나머지 매핑의 기본값은 그대로 둔다. */
    int deleteFacilitySportsByIds(@Param("facilityId") long facilityId, @Param("sportIds") List<Long> sportIds);

    /** 소유한 시설을 논리 삭제한다(담당자 매핑과 종목 매핑은 유지). */
    int deleteFacility(@Param("facilityId") Long facilityId);
}
