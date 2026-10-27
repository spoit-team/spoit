package co.kr.spoit.club.service;

import java.util.List;

import co.kr.spoit.club.domain.ClubRegionOption;
import co.kr.spoit.club.domain.ClubSportOption;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubCreateForm;
import co.kr.spoit.club.dto.ClubDetailResponse;

public interface ClubService {

    List<ClubCardResponse> findClubCards();

    ClubDetailResponse findClubDetail(Long clubId);

    List<ClubSportOption> findSportOptions();

    List<ClubRegionOption> findRegionOptions();

    Long createClub(Long userId, ClubCreateForm form);
}
