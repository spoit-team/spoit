package co.kr.spoit.match.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.ApplicationType;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.match.dto.ActiveFilter;
import co.kr.spoit.match.dto.DateOption;
import co.kr.spoit.match.dto.DistrictOption;
import co.kr.spoit.match.dto.MatchCardView;
import co.kr.spoit.match.dto.MatchCountSummary;
import co.kr.spoit.match.dto.MatchDetailPage;
import co.kr.spoit.match.dto.MatchDetailRow;
import co.kr.spoit.match.dto.MatchListView;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.MatchSearchCondition;
import co.kr.spoit.match.dto.MatchSearchQuery;
import co.kr.spoit.match.dto.ParticipantRow;
import co.kr.spoit.match.dto.SportCount;
import co.kr.spoit.match.dto.SportTab;
import co.kr.spoit.match.mapper.MatchMapper;
import lombok.RequiredArgsConstructor;

/**
 * RES-01 시설·매칭 검색, RES-02 매칭방 상세.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchService {

    /** 검색 가능 기간: 오늘부터 14일 */
    static final int SEARCH_DAYS = 14;

    /** 시간대 선택지 범위 */
    static final int FIRST_HOUR = 6;
    static final int LAST_HOUR = 23;

    /** 서버가 UTC여도 한국 시간 기준. DB에는 NOW() 대신 이 값을 넘긴다. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 목록 노출 상태: 종료·취소 제외 */
    private static final List<String> VISIBLE_STATUSES =
            List.of(MatchStatus.RECRUITING.dbValue(), MatchStatus.MATCHED.dbValue());

    private final MatchMapper matchMapper;
    private final MatchCardAssembler assembler;
    private final MatchDetailAssembler detailAssembler;

    /**
     * @param condition 검색 조건 (정규화되며 모델 "search"로 그대로 쓰인다)
     * @param basePath  필터 해제 링크의 기준 경로 (/matches)
     */
    public MatchListView search(MatchSearchCondition condition, String basePath) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();

        List<SportTab> sports = matchMapper.findSports();
        List<DistrictOption> districts = matchMapper.findDistricts();
        normalize(condition, sports, districts);

        MatchSearchQuery query = toQuery(condition, condition.getSportId(), now, today);
        // 종목 탭 숫자: 종목 외 조건(지역·날짜·시간)만 적용
        MatchSearchQuery withoutSport = toQuery(condition, null, now, today);
        MatchSearchQuery unfiltered = toQuery(new MatchSearchCondition(), null, now, today);

        MatchCountSummary counts = matchMapper.countMatches(query);
        long totalRoomCount = matchMapper.countMatches(unfiltered).getRoomCount();
        List<SportTab> sportTabs = buildSportTabs(sports, matchMapper.countBySport(withoutSport));

        List<MatchRow> rows = matchMapper.findMatches(query);
        List<MatchCardView> cards = toCards(rows, now, today);

        List<DistrictOption> districtOptions = new ArrayList<>();
        districtOptions.add(new DistrictOption(null, "전체 지역"));
        districtOptions.addAll(districts);

        List<DateOption> dates = buildDateOptions(today);
        String sportName = sports.stream()
                .filter(s -> s.getSportId().equals(condition.getSportId()))
                .map(SportTab::getName).findFirst().orElse(null);
        String regionName = districts.stream()
                .filter(d -> d.getRegionId().equals(condition.getRegionId()))
                .map(DistrictOption::getName).findFirst().orElse(null);

        return MatchListView.builder()
                .sports(sportTabs)
                .districts(districtOptions)
                .dates(dates)
                .hourOptions(buildHourOptions())
                .selectedHours(condition.getHours())
                .selectedHoursLabel(hoursLabel(condition.getHours()))
                .selectedSportName(sportName == null ? "전체 종목" : sportName)
                .sportImageUrl(null) // TODO 종목 이미지 경로 확정 후 연결
                .totalRoomCount(totalRoomCount)
                .roomCount(counts.getRoomCount())
                .recruitingCount(counts.getRecruitingCount())
                .matchedCount(counts.getMatchedCount())
                .activeFilters(buildActiveFilters(condition, basePath, sportName, regionName, dates))
                .matches(cards)
                .build();
    }

    /**
     * RES-02 매칭방 상세. 없거나 시설이 삭제된 방이면 empty.
     *
     * @param currentUserId 로그인 회원 (비로그인이면 null), 이미 신청했는지 판단에 쓴다
     */
    public Optional<MatchDetailPage> findDetail(long matchId, Long currentUserId) {
        MatchDetailRow row = matchMapper.findMatchDetail(matchId);
        if (row == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<ParticipantRow> participants = findOccupants(List.of(matchId));
        return Optional.of(detailAssembler.toPage(row, participants, currentUserId, now, now.toLocalDate()));
    }

    /* ---------------- 조회 결과 변환 ---------------- */

    private List<MatchCardView> toCards(List<MatchRow> rows, LocalDateTime now, LocalDate today) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(MatchRow::getMatchId).toList();
        Map<Long, List<ParticipantRow>> byMatch = findOccupants(ids)
                .stream()
                .collect(Collectors.groupingBy(ParticipantRow::getMatchId));

        return rows.stream()
                .map(r -> assembler.toCard(r, byMatch.getOrDefault(r.getMatchId(), Collections.emptyList()), now, today))
                .toList();
    }

    /**
     * 정원을 차지하는 사람: 참가자 행(대기·확정) + 확정 대기 중인 개인 신청.
     * 신청은 확정 전까지 match_participant에 없으므로 여기서 합쳐 기존 Assembler가 그대로 쓰게 한다.
     */
    private List<ParticipantRow> findOccupants(List<Long> matchIds) {
        List<ParticipantRow> rows = new ArrayList<>(
                matchMapper.findParticipants(matchIds, ParticipationStatus.occupyingValues()));
        rows.addAll(matchMapper.findPendingApplicants(matchIds,
                ApplicationStatus.WAITING.dbValue(),
                ApplicationType.INDIVIDUAL.dbValue(),
                ParticipationStatus.WAITING.dbValue()));
        return rows;
    }

    private List<SportTab> buildSportTabs(List<SportTab> sports, List<SportCount> counts) {
        Map<Long, Long> countMap = counts.stream()
                .collect(Collectors.toMap(SportCount::getSportId, SportCount::getRoomCount));

        List<SportTab> tabs = new ArrayList<>();
        tabs.add(SportTab.all(countMap.values().stream().mapToLong(Long::longValue).sum()));
        for (SportTab s : sports) {
            s.setCount(countMap.getOrDefault(s.getSportId(), 0L));
            tabs.add(s);
        }
        return tabs;
    }

    /* ---------------- 검증·정규화 ---------------- */

    /** 존재하지 않는 값·범위 밖 값은 '전체'로 되돌리고, 시간은 "HH:00"으로 맞춘다. */
    private void normalize(MatchSearchCondition c, List<SportTab> sports, List<DistrictOption> districts) {
        if (c.getSportId() != null
                && sports.stream().noneMatch(s -> s.getSportId().equals(c.getSportId()))) {
            c.setSportId(null);
        }
        if (c.getRegionId() != null
                && districts.stream().noneMatch(d -> d.getRegionId().equals(c.getRegionId()))) {
            c.setRegionId(null);
        }
        Integer idx = c.getDateIdx();
        if (idx != null && (idx < 0 || idx >= SEARCH_DAYS)) {
            c.setDateIdx(null);
        }
        List<String> hours = c.getHours() == null ? List.of() : c.getHours();
        c.setHours(hours.stream()
                .map(MatchService::parseHour)
                .filter(Objects::nonNull)
                .filter(h -> h >= FIRST_HOUR && h <= LAST_HOUR)
                .distinct()
                .sorted()
                .map(MatchService::formatHour)
                .collect(Collectors.toCollection(ArrayList::new)));
    }

    /** "18:00" 또는 "18" → 18, 형식이 틀리면 null */
    private static Integer parseHour(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String head = value.strip().split(":")[0];
        try {
            return Integer.parseInt(head);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatHour(int hour) {
        return String.format("%02d:00", hour);
    }

    /**
     * 날짜 선택 시 그날 하루, 미선택 시 검색 기간 전체.
     * 이미 시작한 경기는 제외하기 위해 from은 최소 now.
     */
    private MatchSearchQuery toQuery(MatchSearchCondition c, Long sportId, LocalDateTime now, LocalDate today) {
        LocalDateTime from;
        LocalDateTime to;
        if (c.getDateIdx() != null) {
            LocalDate date = today.plusDays(c.getDateIdx());
            LocalDateTime dayStart = date.atStartOfDay();
            from = now.isAfter(dayStart) ? now : dayStart;
            to = date.plusDays(1).atStartOfDay();
        } else {
            from = now;
            to = today.plusDays(SEARCH_DAYS).atStartOfDay();
        }

        // 정규화 후라 "HH:00" 형식만 남아 있다. 선택한 시간 중 하나라도 걸치는 방을 찾는다.
        List<Integer> hours = c.getHours() == null ? List.of()
                : c.getHours().stream().map(MatchService::parseHour).toList();

        return MatchSearchQuery.builder()
                .sportId(sportId)
                .regionId(c.getRegionId())
                .from(from)
                .to(to)
                .hours(hours)
                .visibleStatuses(VISIBLE_STATUSES)
                .recruitingStatus(MatchStatus.RECRUITING.dbValue())
                .matchedStatus(MatchStatus.MATCHED.dbValue())
                .build();
    }

    /* ---------------- 화면 선택지 ---------------- */

    private List<DateOption> buildDateOptions(LocalDate today) {
        return IntStream.range(0, SEARCH_DAYS)
                .mapToObj(i -> {
                    LocalDate d = today.plusDays(i);
                    return new DateOption(i, d, MatchCardAssembler.dateLabel(d, today));
                })
                .toList();
    }

    private List<String> buildHourOptions() {
        return IntStream.rangeClosed(FIRST_HOUR, LAST_HOUR)
                .mapToObj(MatchService::formatHour)
                .toList();
    }

    /** 각 칩의 url은 그 조건만 뺀 검색 링크 */
    private List<ActiveFilter> buildActiveFilters(MatchSearchCondition c, String basePath,
                                                  String sportName, String regionName, List<DateOption> dates) {
        List<ActiveFilter> filters = new ArrayList<>();
        if (sportName != null) {
            filters.add(new ActiveFilter(sportName,
                    url(basePath, null, c.getRegionId(), c.getDateIdx(), c.getHours())));
        }
        if (regionName != null) {
            filters.add(new ActiveFilter(regionName,
                    url(basePath, c.getSportId(), null, c.getDateIdx(), c.getHours())));
        }
        if (c.getDateIdx() != null) {
            filters.add(new ActiveFilter(dates.get(c.getDateIdx()).getLabel(),
                    url(basePath, c.getSportId(), c.getRegionId(), null, c.getHours())));
        }
        String hoursLabel = hoursLabel(c.getHours());
        if (hoursLabel != null) {
            filters.add(new ActiveFilter(hoursLabel,
                    url(basePath, c.getSportId(), c.getRegionId(), c.getDateIdx(), List.of())));
        }
        return filters;
    }

    private String url(String basePath, Long sportId, Long regionId, Integer dateIdx, List<String> hours) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(basePath);
        if (sportId != null) {
            b.queryParam("sportId", sportId);
        }
        if (regionId != null) {
            b.queryParam("regionId", regionId);
        }
        if (dateIdx != null) {
            b.queryParam("date", dateIdx);
        }
        if (hours != null && !hours.isEmpty()) {
            b.queryParam("hours", hours.toArray());
        }
        return b.build().toUriString();
    }

    /** ["18:00", "19:00"] → "18시, 19시", 비어 있으면 null */
    private String hoursLabel(List<String> hours) {
        if (hours == null || hours.isEmpty()) {
            return null;
        }
        return hours.stream().map(h -> h.substring(0, 2) + "시").collect(Collectors.joining(", "));
    }
}