package co.kr.spoit.club.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import co.kr.spoit.club.domain.ClubRegionOption;
import co.kr.spoit.club.domain.ClubSportOption;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubCreateForm;
import co.kr.spoit.club.dto.ClubDetailResponse;
import co.kr.spoit.club.dto.ClubManageResponse;
import co.kr.spoit.club.dto.ClubMemberHistoryView;
import co.kr.spoit.club.dto.ClubWithdrawalBlocks;
import co.kr.spoit.club.dto.ClubSearchForm;
import co.kr.spoit.club.dto.ClubSportTabResponse;
import co.kr.spoit.club.dto.MyClubResponse;
import co.kr.spoit.club.dto.MyJoinRequestResponse;
import co.kr.spoit.mat.dto.ParticipantHistoryView;

public interface ClubService {

    // 활성화 동호회 이름 중복 예외 처리
    static class ClubNameExistException extends Exception {
    }

    // 동호회 목록 카드 조회
    List<ClubCardResponse> findClubCards(ClubSearchForm filters);

    // 종목 상단 탭
    List<ClubSportTabResponse> findSportTabs(Long selectedSportId);

    // 동호회 상세 조회(없으면 null)
    ClubDetailResponse findClubDetail(Long clubId, Long userId);

    // 동호회 만들기 종목 선택
    List<ClubSportOption> findSportOptions();

    // 동호회 만들기 지역 선택
    List<ClubRegionOption> findRegionOptions();

    // 동호회 만들기: 동호회,지역,동호회장 한 번에 저장
    Long createClub(Long userId, ClubCreateForm form) throws ClubNameExistException;

    // 내 동호회 관리 화면
    ClubManageResponse findManagedClub(Long userId, Long clubId);

    // 내 동호회 관리 화면 위쪽 선택 버튼 - 내 동호회 목록(activeClubId가 선택된 동호회)
    List<MyClubResponse> findMyClubs(Long userId, Long activeClubId);

    // 가입 신청
    void applyJoin(Long userId, Long clubId, String message);

    // 내 가입 신청 목록(대기중) - 관리 화면 맨위
    List<MyJoinRequestResponse> findMyPendingJoinRequests(Long userId);

    // 가입 신청자 개인 이력(동호회장만)
    Optional<ParticipantHistoryView> findApplicantHistory(Long clubId, Long joinRequestId, Long userId,
            LocalDateTime now);

    // 동호회 이력(로그인 회원 누구나)
    Optional<ClubMemberHistoryView> findMemberHistory(Long clubId, Long clubMemberId, LocalDateTime now);

    // 가입 신청 승인
    void approveJoin(Long userId, Long clubId, Long joinRequestId);

    // 가입 신청 거절
    void rejectJoin(Long userId, Long clubId, Long joinRequestId);

    // 가입 신청 취소
    void cancelJoin(Long userId, Long clubId, Long joinRequestId);

    // 동호회장 위임
    void transferLeader(Long userId, Long clubId, Long clubMemberId);

    // 동호회 탈퇴
    void leaveClub(Long userId, Long clubId);

    // 동호회원 추방
    void kickMember(Long userId, Long clubId, Long clubMemberId, String kickReason);

    // 동호회 해체
    void disbandClub(Long userId, Long clubId, String clubNameConfirmation);

    // 동호회 정보 수정
    void updateClub(Long userId, Long clubId, ClubCreateForm form) throws ClubNameExistException;

    /**
     * 동호회 행을 잠그고 최신 값을 돌려준다. 없거나 해체됐으면 null.
     *
     * <p>
     * 다른 도메인이 동호회 행을 먼저 잠가야 할 때(팀 매치 신청의 잠금 순서) 사용한다.
     * 반드시 쓰기 트랜잭션 안에서 호출해야 한다.
     * </p>
     */
    co.kr.spoit.club.domain.ClubSummary lockActiveClub(Long clubId);

    // 회원 탈퇴를 막는 동호회 소속(동호회장·모집 중 팀 매치 신청/참가)
    ClubWithdrawalBlocks findWithdrawalBlocks(Long userId);
}
