package co.kr.spoit.club.service;

import java.util.List;

import co.kr.spoit.club.domain.ClubRegionOption;
import co.kr.spoit.club.domain.ClubSportOption;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubCreateForm;
import co.kr.spoit.club.dto.ClubDetailResponse;
import co.kr.spoit.club.dto.ClubManageResponse;
import co.kr.spoit.club.dto.MyClubResponse;

public interface ClubService {

    // 활성화 동호회 이름 중복 예외 처리
    static class ClubNameExistException extends Exception {
    }

    // 동호회 목록 카드 조회
    List<ClubCardResponse> findClubCards();

    // 동호회 상세 조회(없으면 null)
    ClubDetailResponse findClubDetail(Long clubId);

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

}
