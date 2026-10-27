package co.kr.spoit.club.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.TreeSet;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.club.domain.Club;
import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubRegionOption;
import co.kr.spoit.club.domain.ClubSportOption;
import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubCreateForm;
import co.kr.spoit.club.dto.ClubDetailResponse;
import co.kr.spoit.club.mapper.ClubMapper;

@Service
@Transactional(readOnly = true)
public class ClubServiceImpl implements ClubService {

    private final ClubMapper clubMapper;
    private static final int NAME_MAX_LENGTH = 30;
    private static final int INTRO_MAX_LENGTH = 240;
    private static final int MIN_MEMBERS = 2;
    private static final int MAX_MEMBERS = 30;
    private static final String DUPLICATE_NAME_MESSAGE = "이미 사용 중인 팀이름입니다.";

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
    public List<ClubSportOption> findSportOptions() {
        return clubMapper.selectClubSportOptions();
    }

    @Override
    public List<ClubRegionOption> findRegionOptions() {
        return clubMapper.selectClubRegionOptions();
    }

    @Override
    @Transactional
    public Long createClub(Long userId, ClubCreateForm form) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        if (form == null) {
            throw new IllegalArgumentException("입력 정보를 확인해 주세요.");
        }

        String clubName = trim(form.getClubName());
        if (clubName.isEmpty() || clubName.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("팀 이름은 1~30자로 입력해 주세요.");
        }
        String intro = trim(form.getIntro());
        if (intro.isEmpty() || intro.length() > INTRO_MAX_LENGTH) {
            throw new IllegalArgumentException("팀 소개는 1~240자로 입력해 주세요.");
        }
        Integer maxMembers = form.getMaxMembers();
        if (maxMembers == null || maxMembers < MIN_MEMBERS || maxMembers > MAX_MEMBERS) {
            throw new IllegalArgumentException("최대 팀원 수는 2~30명으로 입력해주세요.");
        }
        String activityDays = normalizeDays(form.getActivityDays());
        LocalTime start = form.getActivityStartTime();
        LocalTime end = form.getActivityEndTime();
        if (start == null || end == null) {
            throw new IllegalArgumentException("활동 시작 시간과 종료 시간을 입력해주세요.");
        }
        if (start.equals(end)) {
            throw new IllegalArgumentException("활동 시작 시간과 종료시간이 같을 수 없습니다.");
        }

        if (form.getSportId() == null || !clubMapper.existsSport(form.getSportId())) {
            throw new IllegalArgumentException("선택한 종목을 찾을 수 없습니다.");
        }
        if (form.getRegionId() == null || !clubMapper.existsRegion(form.getRegionId())) {
            throw new IllegalArgumentException("선택한 지역을 찾을 수 없습니다.");
        }
        if (clubMapper.existsActiveClubName(clubName)) {
            throw new IllegalArgumentException(DUPLICATE_NAME_MESSAGE);
        }

        Club club = new Club();
        club.setSportId(form.getSportId());
        club.setClubName(clubName);
        club.setIntro(intro);
        club.setMaxMembers(maxMembers);
        club.setActivityDays(activityDays);
        club.setActivityStartTime(start);
        club.setActivityEndTime(end);

        try {
            clubMapper.insertClub(club);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException(DUPLICATE_NAME_MESSAGE, exception);
        }
        clubMapper.insertClubRegion(club.getClubId(), form.getRegionId());
        clubMapper.insertClubMember(club.getClubId(), userId);
        return club.getClubId();
    }

    private static String normalizeDays(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("정기 활동 요일을 하나 이상 선택해 주세요.");
        }
        TreeSet<Integer> sorted = new TreeSet<>();
        for (Integer day : days) {
            if (day == null || day < 1 || day > 7) {
                throw new IllegalArgumentException("활동 요일 값이 올바르지 않습니다.");
            }
            sorted.add(day);
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Integer day : sorted) {
            joiner.add(String.valueOf(day));
        }
        return joiner.toString();

    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
