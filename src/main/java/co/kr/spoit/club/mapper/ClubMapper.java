package co.kr.spoit.club.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.club.domain.Club;
import co.kr.spoit.club.domain.ClubJoinRequest;
import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubRegionOption;
import co.kr.spoit.club.domain.ClubSportOption;
import co.kr.spoit.club.domain.ClubSummary;

@Mapper
public interface ClubMapper {

    // 동호회 목록(조건 null이면 그 조건은 빠짐)
    List<ClubSummary> selectClubSummaryList(@Param("query") String query, @Param("sportId") Long sportId,
            @Param("regionId") Long regionId, @Param("recruitingOnly") boolean recruitingOnly,
            @Param("sort") String sort);

    // 동호회 상세(해체된 동호회 제외)
    ClubSummary selectClubDetail(@Param("clubId") Long clubId);

    // 활성 동호회원 (동호회장 먼저, 가입순)
    List<ClubMember> selectClubMembers(@Param("clubId") Long clubId);

    // 동호회 만들기 폼, 선택지
    List<ClubRegionOption> selectClubRegionOptions();

    List<ClubSportOption> selectClubSportOptions();

    // 저장 전 확인: 활성 동호회 이름 중복, 종목과 지역 존재
    boolean existsActiveClubName(@Param("clubName") String clubName);

    boolean existsSport(@Param("sportId") Long sportId);

    boolean existsRegion(@Param("regionId") Long regionId);

    // 저장: 새 번호 부여
    int insertClub(Club club);

    int insertClubRegion(@Param("clubId") Long clubId, @Param("regionId") Long regionId);

    int insertClubMember(@Param("clubId") Long clubId, @Param("userId") Long userId);

    List<ClubSummary> selectMyClubs(@Param("userId") Long userId);

    boolean existsActiveMember(@Param("clubId") Long clubId, @Param("userId") Long userId);

    // 동호회 가입 신청 번호(대기중)
    Long selectPendingJoinRequestId(@Param("clubId") Long clubId, @Param("userId") Long userId);

    // 쓰기 전에 동호회 잠금
    ClubSummary selectActiveClubForUpdate(@Param("clubId") Long clubId);

    // 활성 동호회원 수
    int countActiveMembers(@Param("clubId") Long clubId);

    // 가입 신청 저장
    int insertJoinRequest(@Param("clubId") Long clubId, @Param("userId") Long userId, @Param("message") String message);

    // 동호회장인지 확인
    boolean existsActiveLeader(@Param ("clubId") Long clubId, @Param ("userId") Long userId);

    // 대기중 가입 신청 목록(먼저 신청 순)
    List<ClubJoinRequest> selectPendingJoinRequests(@Param ("clubId") Long clubId);
}
