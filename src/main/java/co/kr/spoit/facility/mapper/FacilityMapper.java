package co.kr.spoit.facility.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.facility.domain.FacilitySummary;

@Mapper
public interface FacilityMapper {

    /**
     * 지정한 회원이 담당자로 매핑된 삭제되지 않은 시설만 조회한다.
     * 시설별 종목은 한 행으로 집계해 시설이 중복되지 않게 한다.
     */
    List<FacilitySummary> selectManagedFacilities(@Param("userId") Long userId);
}
