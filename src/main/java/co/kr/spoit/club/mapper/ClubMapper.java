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
import co.kr.spoit.club.dto.ClubBlockRow;

@Mapper
public interface ClubMapper {

        // 동호회 목록(조건 null이면 그 조건은 빠짐)
        List<ClubSummary> selectClubs(@Param("query") String query, @Param("sportId") Long sportId,
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

        int insertClubLeader(@Param("clubId") Long clubId, @Param("userId") Long userId);

        List<ClubSummary> selectMyClubs(@Param("userId") Long userId);

        boolean existsActiveMember(@Param("clubId") Long clubId, @Param("userId") Long userId);

        // 내 동호회 가입 신청 번호(대기중)
        Long selectMyPendingJoinRequestId(@Param("clubId") Long clubId, @Param("userId") Long userId);

        // 쓰기 전에 동호회 잠금
        ClubSummary selectActiveClubForUpdate(@Param("clubId") Long clubId);

        // 활성 동호회원 수
        int countActiveMembers(@Param("clubId") Long clubId);

        // 가입 신청 저장
        int insertJoinRequest(@Param("clubId") Long clubId, @Param("userId") Long userId,
                        @Param("message") String message);

        // 동호회장인지 확인
        boolean existsActiveLeader(@Param("clubId") Long clubId, @Param("userId") Long userId);

        // 대기중 가입 신청 목록(먼저 신청 순)
        List<ClubJoinRequest> selectPendingJoinRequests(@Param("clubId") Long clubId);

        // 처리할 가입 신청(대기중)
        ClubJoinRequest selectPendingJoinRequestById(@Param("clubId") Long clubId,
                        @Param("joinRequestId") Long joinRequestId);

        // 가입 신청 승인, 거절
        int updateJoinRequestStatus(@Param("joinRequestId") Long joinRequestId, @Param("status") String status);

        // 승인된 신청자를 동호회원으로 추가
        int insertJoinedMember(@Param("clubId") Long clubId, @Param("userId") Long userId);

        // 내 가입 신청 취소(대기중만)
        int cancelJoinRequest(@Param("clubId") Long clubId, @Param("joinRequestId") Long joinRequestId,
                        @Param("userId") Long userId);

        // 동호회장 위임: 남을 동호회장으로
        int updateRoleToLeader(@Param("clubId") Long clubId, @Param("clubMemberId") Long clubMemberId);

        // 동호회장 위임: 나를 동호회원으로
        int updateRoleToMember(@Param("clubId") Long clubId, @Param("userId") Long userId);

        // 모집 중인 팀 매치에 신청됐는지
        boolean existsRecruitingTeamMatch(@Param("clubId") Long clubId, @Param("userId") Long userId);

        // 회원이 동호회장으로 있는 활성 동호회(탈퇴 차단 사유)
        List<ClubBlockRow> selectLeaderClubBlocks(@Param("userId") Long userId);

        // 모집 중인 팀 매치에 신청·참가 중인 동호회(탈퇴 차단 사유)
        List<ClubBlockRow> selectRecruitingTeamMatchClubBlocks(@Param("userId") Long userId);

        // 동호회 탈퇴
        int updateMemberLeave(@Param("clubId") Long clubId, @Param("userId") Long userId);

        // 추방할 동호회원
        ClubMember selectActiveMemberById(@Param("clubId") Long clubId, @Param("clubMemberId") Long clubMemberId);

        // 동호회원 추방
        int updateMemberKick(@Param("clubId") Long clubId, @Param("clubMemberId") Long clubMemberId,
                        @Param("kickReason") String kickReason);

        // 해체: 대기 중 가입 신청 취소
        int cancelPendingJoinRequests(@Param("clubId") Long clubId);

        // 해체: 동회회장 이탈
        int updateLeaderDisband(@Param("clubId") Long clubId, @Param("userId") Long userId);

        // 해체: 동호회 해체 시각 기록
        int updateClubDisband(@Param("clubId") Long clubId);

        // 수정: 활성 동호회 정보 수정
        int updateClub(Club club);

        // 수정: 활성 동호회 지역 수정(지우고 다시 넣기)
        int deleteClubRegions(@Param("clubId") Long clubId);

        // 수정: 나 말고 같은 이름의 활성 동호회가 있는지 확인
        boolean existsOtherActiveClubName(@Param("clubId") Long clubId, @Param("clubName") String clubName);
}
