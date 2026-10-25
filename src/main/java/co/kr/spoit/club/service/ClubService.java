package co.kr.spoit.club.service;

import java.util.List;

import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubDetailResponse;

public interface ClubService {

    List<ClubCardResponse> findClubCards();

    ClubDetailResponse findClubDetail(Long clubId);
}
