package co.kr.spoit.match.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.match.dto.DistrictOption;
import co.kr.spoit.match.dto.MatchCountSummary;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.MatchSearchQuery;
import co.kr.spoit.match.dto.ParticipantRow;
import co.kr.spoit.match.dto.SportCount;
import co.kr.spoit.match.dto.SportTab;

@Mapper
public interface MatchMapper {

    /** 조건에 맞는 매칭방 목록 (시작 시각 순) */
    List<MatchRow> findMatches(MatchSearchQuery query);

    /** 조건에 맞는 매칭방 집계 */
    MatchCountSummary countMatches(MatchSearchQuery query);

    /** 종목별 매칭방 수 (종목 탭 숫자) */
    List<SportCount> countBySport(MatchSearchQuery query);

    /** 여러 매칭방의 참가자를 한 번에 조회 (N+1 방지) */
    List<ParticipantRow> findParticipants(@Param("matchIds") List<Long> matchIds,
                                          @Param("statuses") List<String> statuses);

    /* 검색 필터 선택지: sport/region 담당자가 공용 조회를 만들면 그쪽으로 옮긴다. */

    List<SportTab> findSports();

    List<DistrictOption> findDistricts();
}
