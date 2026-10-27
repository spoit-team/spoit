package co.kr.spoit.club.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubRegionOption;
import co.kr.spoit.club.domain.ClubSportOption;
import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubDetailResponse;
import co.kr.spoit.club.mapper.ClubMapper;

@Service 
@Transactional(readOnly = true)
public class ClubServiceImpl implements ClubService {

    private final ClubMapper clubMapper;

    public ClubServiceImpl(ClubMapper clubMapper) {
        this.clubMapper = clubMapper;
    }

    @Override
    public List<ClubCardResponse> findClubCards() {
        List<ClubSummary> clubs = clubMapper.selectClubSummaryList(null, null, null, false, "recent");

        List<ClubCardResponse> cards = new ArrayList<>();
        for (ClubSummary club : clubs) {
            cards.add(ClubCardResponse.from(club));
        }
        return cards;
    }

    @Override 
    public ClubDetailResponse findClubDetail(Long clubId) {
        ClubSummary club = clubMapper.selectClubDetail(clubId);
        if (club == null) {
            return null;
        }
        List<ClubMember> members = clubMapper.selectClubMembers(clubId);
        return ClubDetailResponse.from(club, members);
    }

    @Override 
    public List<ClubSportOption> findSportOptions(){
        return clubMapper.selectClubSportOptions();
    }

    @Override 
    public List<ClubRegionOption> findRegionOptions(){
        return clubMapper.selectClubRegionOptions();
    }
}

