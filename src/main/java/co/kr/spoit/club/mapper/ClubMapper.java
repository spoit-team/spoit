package co.kr.spoit.club.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.club.domain.Club;
import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubRegionOption;
import co.kr.spoit.club.domain.ClubSportOption;
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

    List<ClubRegionOption> selectClubRegionOptions();

    List<ClubSportOption> selectClubSportOptions();

    boolean existsActiveClubName(@Param ("clubName") String clubName);
    boolean existsSport(@Param ("sportId") Long sportId);
    boolean existsRegion(@Param ("regionId") Long regionId);

    int insertClub(Club club);
    int insertClubRegion(@Param ("clubId") Long clubId, @Param ("regionId") Long regionId);
    int insertClubMember(@Param ("clubId") Long clubId, @Param ("userId") Long userId);
}
