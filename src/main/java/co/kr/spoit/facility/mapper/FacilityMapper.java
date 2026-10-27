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

    /** 기존 종목 매핑을 모두 지우고 새로 저장한다. */
    int deleteFacilitySports(@Param("facilityId") Long facilityId);

    /** 소유한 시설을 논리 삭제한다(담당자 매핑과 종목 매핑은 유지). */
    int deleteFacility(@Param("facilityId") Long facilityId);
}
