package co.kr.spoit.club.service;

import java.util.List;

import co.kr.spoit.club.dto.ClubCardResponse;

public interface ClubService {

    List<ClubCardResponse> findClubCards();
}
