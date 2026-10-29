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
import co.kr.spoit.club.dto.ClubManageResponse;
import co.kr.spoit.club.dto.MyClubResponse;
import co.kr.spoit.club.mapper.ClubMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ClubServiceImpl implements ClubService {

    private final ClubMapper clubMapper;
    private static final int NAME_MAX_LENGTH = 30;
    private static final int INTRO_MAX_LENGTH = 240;
    private static final int MIN_MEMBERS = 2;
    private static final int MAX_MEMBERS = 30;

    // 최신순 전체 조회(검색 미구현)
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
            log.info("없는 동호회 조회: clubId={}", clubId);
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

    // 동호회 만들기 저장
    @Override
    @Transactional
    public Long createClub(Long userId, ClubCreateForm form) throws ClubNameExistException {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        if (form == null) {
            throw new IllegalArgumentException("입력 정보를 확인해 주세요.");
        }

        // 1. 브라우저 입력 정리, 검사
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
        // 익일로 넘김, 같은 시각만 거부
        if (start.equals(end)) {
            throw new IllegalArgumentException("활동 시작 시간과 종료시간이 같을 수 없습니다.");
        }

        // 2. 종목,지역 조회 및 활성 동호회 이름 중복 검사
        if (form.getSportId() == null || !clubMapper.existsSport(form.getSportId())) {
            throw new IllegalArgumentException("선택한 종목을 찾을 수 없습니다.");
        }
        if (form.getRegionId() == null || !clubMapper.existsRegion(form.getRegionId())) {
            throw new IllegalArgumentException("선택한 지역을 찾을 수 없습니다.");
        }
        if (clubMapper.existsActiveClubName(clubName)) {
            throw new ClubNameExistException();
        }

        // 3. 저장(동호회,지역,동호회장 순서, 모두 성공해야 함)
        Club club = Club.builder()
                .sportId(form.getSportId())
                .clubName(clubName)
                .intro(intro)
                .maxMembers(maxMembers)
                .activityDays(activityDays)
                .activityStartTime(start)
                .activityEndTime(end)
                .build();
        log.info("동호회 저장 직전: {}", club);

        try {
            clubMapper.insertClub(club);
        } catch (DuplicateKeyException exception) {
            // 동시에 같은 이름을 만들면 UK로 막음
            log.warn("동호회 이름 동시 저장 충돌: {}", clubName);
            throw new ClubNameExistException();
        }
        clubMapper.insertClubRegion(club.getClubId(), form.getRegionId());
        clubMapper.insertClubMember(club.getClubId(), userId);
        log.info("동호회 저장 완료: clubId={}, userId={}", club.getClubId(), userId);
        return club.getClubId();
    }

    // 요일 번호(1~7)를 "1,3" 형태로 만듬
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

    // null도 빈 문자열로 바꿔서 빈 값 검사함
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    // 내 동호회 관리 조회
    @Override
    public ClubManageResponse findManagedClub(Long userId, Long clubId) {
        Long targetClubId = clubId;
        // 선택 없이 내 동호회 조회(동호회장인 곳 먼저, 최근 가입 순)
        if (targetClubId == null) {
            List<ClubSummary> myClubs = clubMapper.selectMyClubs(userId);
            if (myClubs.isEmpty()) {
                log.info("관리할 동호회 없음: userId={}", userId);
                return null;
            }
            targetClubId = myClubs.get(0).getClubId();
        }

        // 내 동호회가 아니면 거부
        if (!clubMapper.existsActiveMember(targetClubId, userId)) {
            throw new IllegalArgumentException("내가 속한 팀만 관리할 수 있습니다.");
        }

        ClubSummary club = clubMapper.selectClubDetail(targetClubId);
        if (club == null) {
            log.info("없는 동호회 조회: clubId={}", targetClubId);
            return null;
        }
        List<ClubMember> members = clubMapper.selectClubMembers(targetClubId);
        return ClubManageResponse.from(club, members, userId);
    }

    // 내 동호회 선택 버튼 응답
    @Override 
    public List<MyClubResponse> findMyClubs(Long userId, Long activeClubId){
        List<ClubSummary> clubs = clubMapper.selectMyClubs(userId);

        List<MyClubResponse> result = new ArrayList<>();
        for(ClubSummary club : clubs){
            result.add(MyClubResponse.from(club, activeClubId));
        }
        return result;
    }
}