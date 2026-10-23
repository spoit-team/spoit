package co.kr.spoit.club.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubSummary;

@Mapper 
public interface ClubMapper {

    List<ClubSummary> selectClubSummaryList(@Param("query") String query
                                            ,@Param("sportId") Long sportId
                                            ,@Param("regionId") Long regionId
                                            ,@Param("recruitingOnly") boolean recruitingOnly
                                            ,@Param("sort") String sort
    );

    ClubSummary selectClubDetail(@Param("clubId") Long clubId);

    List<ClubMember> selectClubMembers(@Param("clubId") Long clubId);
}
