package co.kr.spoit.match.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.mat.domain.RatingStatus;
import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.ApplicationType;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.match.domain.RoomStatusFilter;
import co.kr.spoit.match.dto.FacilityDashboard;
import co.kr.spoit.match.dto.RoomManagementApplicationRow;
import co.kr.spoit.match.dto.RoomManagementDetail;
import co.kr.spoit.match.dto.RoomManagementFacilitySport;
import co.kr.spoit.match.dto.RoomManagementPage;
import co.kr.spoit.match.dto.RoomManagementParticipantRow;
import co.kr.spoit.match.dto.RoomManagementQuery;
import co.kr.spoit.match.dto.RoomManagementRecentRequestRow;
import co.kr.spoit.match.dto.RoomManagementRow;
import co.kr.spoit.match.dto.RoomManagementSport;
import co.kr.spoit.match.mapper.RoomManagementMapper;

/**
 * 시설 관리자의 담당 시설 매칭방 조회(목록·검색·상세)를 담당한다.
 *
 * <p>조회는 모두 {@code facility_manager} 기준으로 제한하며, 다른 관리자의 시설·삭제된 시설은
 * 결과에 포함되지 않는다. 정원 점유 기준은 공개 검색과 같은 기준
 * ({@code match_participant} 대기·확정 + 확정 대기 중인 개인 신청)을 쓴다.</p>
 */
@Service
@Transactional(readOnly = true)
public class RoomManagementService {

    /** 검색어 최대 길이. 넘는 입력은 잘라서 조회한다. */
    static final int QUERY_MAX_LENGTH = 100;

    /** LIKE 이스케이프 문자. SQL의 {@code ESCAPE '!'}와 같아야 한다. */
    private static final String LIKE_ESCAPE = "!";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** "2026-10-29(수)" 형식. 연도를 포함해 과거·미래 방을 구분한다. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd(E)", Locale.KOREAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final String TONE_GOOD = "good";
    private static final String TONE_WARN = "warn";
    private static final String TONE_BAD = "bad";
    private static final String TONE_NEUTRAL = "neutral";

    private static final String TEAM_UNASSIGNED = "미배정";

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 대시보드 최근 매칭방 표시 건수 */
    static final int RECENT_MATCH_LIMIT = 5;

    /** 대시보드 최근 요청(참가 신청) 표시 건수 */
    static final int RECENT_REQUEST_LIMIT = 5;

    private final RoomManagementMapper roomManagementMapper;
    private final Clock clock;

    @Autowired
    public RoomManagementService(RoomManagementMapper roomManagementMapper) {
        this(roomManagementMapper, Clock.system(ZONE));
    }

    RoomManagementService(RoomManagementMapper roomManagementMapper, Clock clock) {
        this.roomManagementMapper = roomManagementMapper;
        this.clock = clock;
    }

    /**
     * 담당 시설의 매칭방 목록을 검색 조건과 함께 조회한다.
     *
     * <p>관리자 식별자가 없으면 빈 페이지를 돌려준다. 상태·종목·시설·검색어는 알 수 없는 값을
     * '전체'로 되돌리고, 되돌린 값을 필터 상태로 그대로 노출한다.</p>
     *
     * @param facilityId 담당 시설 필터. null·양수 아님·담당 밖 시설·삭제된 시설이면 '전체'
     */
    public RoomManagementPage findRooms(Long managerUserId, String q, String status, Long sportId,
            Long facilityId) {
        RoomStatusFilter statusFilter = RoomStatusFilter.from(status);
        if (!hasManager(managerUserId)) {
            return RoomManagementPage.empty();
        }

        List<RoomManagementSport> sports = nullSafe(roomManagementMapper.findSports(managerUserId)).stream()
                .filter(sport -> sport != null && sport.getSportId() != null)
                .toList();
        Long normalizedSportId = normalizeSportId(sportId, sports);
        Long normalizedFacilityId = normalizeFacilityId(managerUserId, facilityId);
        String normalizedQ = normalizeQuery(q);

        LocalDateTime now = LocalDateTime.now(clock);
        List<RoomManagementPage.Match> matches = new ArrayList<>();
        RoomManagementQuery query = buildQuery(managerUserId, null, escapeLikePattern(normalizedQ),
                statusFilter.dbValue(), normalizedSportId, normalizedFacilityId);
        for (RoomManagementRow row : nullSafe(roomManagementMapper.findRooms(query))) {
            if (row != null) {
                matches.add(toListMatch(row, now));
            }
        }

        List<RoomManagementPage.Sport> sportItems = sports.stream()
                .map(sport -> new RoomManagementPage.Sport(sport.getSportId(), sport.getName()))
                .toList();

        List<RoomManagementPage.FacilitySport> facilitySports = new ArrayList<>();
        for (RoomManagementFacilitySport item : nullSafe(roomManagementMapper.findFacilitySports(managerUserId))) {
            if (item != null && item.getFacilityId() != null && item.getSportId() != null) {
                facilitySports.add(new RoomManagementPage.FacilitySport(item.getFacilityId(), item.getSportId(),
                        label(item.getName(), "종목 미설정")));
            }
        }

        return RoomManagementPage.of(matches, sportItems,
                new RoomManagementPage.Filters(normalizedQ, statusFilter.value(), normalizedSportId,
                        normalizedFacilityId),
                facilitySports);
    }

    /**
     * 담당 시설 전체의 매칭방 현황을 대시보드용으로 집계한다.
     *
     * <p>화면의 검색어·상태·종목·시설 필터와 무관하게 인증 주체의 담당 활성 시설 전체를 대상으로
     * 한다. 목록 표시용 {@link RoomManagementPage}의 문자열 DTO는 날짜를 다시 해석해야 하므로 쓰지
     * 않고, 소유권 제한이 걸린 {@code findRooms}의 raw 행을 필터 없이 조회해 시각·상태로 집계한다.
     * 목록 조회와 같은 쿼리를 한 번 더 실행하지만 담당 범위 계산을 중복 구현하지 않기 위한 선택이다.</p>
     *
     * <p>관리자 식별자가 없으면 매퍼를 호출하지 않고 0건 집계를 돌려준다. 차트의 '이용 완료'는
     * DB 종료({@link MatchStatus#CLOSED}) 상태만 뜻하고 취소는 포함하지 않는다.</p>
     *
     * <p>최근 요청은 담당 시설 매칭방에 접수된 신청을 상태와 무관하게 최신 접수순으로 최대
     * {@link #RECENT_REQUEST_LIMIT}건 담는다. 팀 신청은 대표 행만 조회해 하나의 신청으로 표현한다.</p>
     */
    public FacilityDashboard findDashboard(Long managerUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        String todayLabel = now.format(DATE);
        String collectedAt = now.format(DATE_TIME);
        if (!hasManager(managerUserId)) {
            return FacilityDashboard.empty(todayLabel, collectedAt);
        }

        RoomManagementQuery query = buildQuery(managerUserId, null, null, null, null, null);
        List<RoomManagementRow> rows = new ArrayList<>();
        for (RoomManagementRow row : nullSafe(roomManagementMapper.findRooms(query))) {
            if (row != null) {
                rows.add(row);
            }
        }

        int matchCount = rows.size();
        int recruitingCount = 0;
        int[] hourlyCounts = new int[24];
        int[] completedCounts = new int[24];
        LocalDate today = now.toLocalDate();
        for (RoomManagementRow row : rows) {
            if (MatchStatus.RECRUITING.dbValue().equals(row.getStatus())) {
                recruitingCount++;
            }
            LocalDateTime startAt = row.getStartAt();
            if (startAt == null || !startAt.toLocalDate().equals(today)) {
                continue;
            }
            int hour = startAt.getHour();
            hourlyCounts[hour]++;
            if (MatchStatus.CLOSED.dbValue().equals(row.getStatus())) {
                completedCounts[hour]++;
            }
        }

        int maxCount = 0;
        for (int count : hourlyCounts) {
            maxCount = Math.max(maxCount, count);
        }
        List<FacilityDashboard.HourlyBar> hourlyBars = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourlyBars.add(new FacilityDashboard.HourlyBar(hourlyCounts[hour], completedCounts[hour],
                    height(hourlyCounts[hour], maxCount), height(completedCounts[hour], maxCount),
                    String.format("%02d시", hour)));
        }

        List<RoomManagementPage.Match> recentMatches = rows.stream()
                .filter(row -> row.getMatchId() != null)
                .sorted(Comparator.comparing(RoomManagementRow::getMatchId).reversed())
                .limit(RECENT_MATCH_LIMIT)
                .map(row -> toListMatch(row, now))
                .toList();

        List<FacilityDashboard.RecentRequest> recentRequests = new ArrayList<>();
        for (RoomManagementRecentRequestRow request : nullSafe(
                roomManagementMapper.findRecentApplications(managerUserId, RECENT_REQUEST_LIMIT))) {
            if (request != null) {
                recentRequests.add(toRecentRequest(request));
            }
        }

        return new FacilityDashboard(matchCount, recruitingCount, hourlyBars, recentMatches,
                recentRequests, todayLabel, collectedAt);
    }

    /**
     * 대시보드 최근 요청 한 건을 화면 문구로 만든다. 상세는 기존 운영 화면
     * ({@code /admin/operations?matchId=})이 그대로 맡으므로 매칭방 ID만 넘긴다.
     */
    private static FacilityDashboard.RecentRequest toRecentRequest(RoomManagementRecentRequestRow row) {
        return new FacilityDashboard.RecentRequest(
                row.getMatchId(),
                label(row.getMatchTitle(), "제목 없음"),
                label(row.getFacilityName(), "시설 미설정"),
                recentApplicantName(row),
                createdAtLabel(row.getCreatedAt()),
                applicationStatusLabel(row.getStatus()),
                applicationStatusTone(row.getStatus()));
    }

    /** 동호회 신청은 신청자(동호회장) 대신 동호회명을 우선 보여 준다. */
    private static String recentApplicantName(RoomManagementRecentRequestRow row) {
        if (ApplicationType.CLUB.dbValue().equals(row.getApplicationType()) && !isBlank(row.getClubName())) {
            return row.getClubName().strip();
        }
        return label(row.getApplicantName(), "신청자 미상");
    }

    /** 24칸 중 최대값에 대한 상대 백분율. 모두 0이면 0이다. */
    private static int height(int count, int maxCount) {
        if (maxCount <= 0) {
            return 0;
        }
        return (int) Math.round(count * 100.0 / maxCount);
    }

    /**
     * 담당 시설 매칭방의 상세(신청·참가자 포함)를 조회한다.
     *
     * <p>관리자 식별자가 없거나, 매칭방 ID가 양수가 아니거나, 존재하지 않거나, 삭제된 시설이거나,
     * 다른 관리자의 시설이면 {@code null}을 돌려주고 방 정보를 노출하지 않는다.</p>
     */
    public RoomManagementDetail findDetail(Long managerUserId, Long matchId) {
        if (!hasManager(managerUserId) || matchId == null || matchId <= 0) {
            return null;
        }

        RoomManagementQuery query = buildQuery(managerUserId, matchId, null, null, null, null);
        RoomManagementRow row = roomManagementMapper.findRoom(query);
        if (row == null) {
            return null;
        }

        // 신청 내역은 기록이라 팀 배정 뒤에도 상태와 관계없이 모두 남긴다. 배정 뒤에는 참가자 표가 함께 보인다.
        boolean teamAssigned = isTeamAssigned(row.getStatus());
        boolean decisionOpen = MatchStatus.RECRUITING.dbValue().equals(row.getStatus())
                && row.getStartAt() != null && row.getStartAt().isAfter(LocalDateTime.now(clock));
        List<RoomManagementDetail.Application> applications = new ArrayList<>();
        for (RoomManagementApplicationRow application : nullSafe(roomManagementMapper.findApplications(query))) {
            if (application != null) {
                applications.add(toApplication(application, decisionOpen));
            }
        }

        List<RoomManagementDetail.Participant> participants = new ArrayList<>();
        if (teamAssigned) {
            for (RoomManagementParticipantRow participant : nullSafe(roomManagementMapper.findParticipants(query))) {
                if (participant != null) {
                    participants.add(toParticipant(participant));
                }
            }
        }

        return new RoomManagementDetail(
                row.getMatchId(),
                label(row.getTitle(), "제목 없음"),
                label(row.getFacilityName(), "시설 미설정"),
                label(row.getSportName(), "종목 미설정"),
                dateLabel(row),
                participationLabel(row),
                statusLabel(row.getStatus()),
                statusTone(row.getStatus()),
                teamAssigned,
                applications,
                participants,
                teamAssigned ? ratingOf(row) : null);
    }

    /**
     * 레이팅 반영 섹션. 제외 가능 조건은 레이팅 제외 API({@code MatchResultService.excludeRating})와 맞춘다:
     * 팀 배정이 끝났고, 경기가 시작됐고, 아직 반영·제외되지 않은 경기다. 알 수 없는 상태 값이면 섹션을 숨긴다.
     */
    private RoomManagementDetail.Rating ratingOf(RoomManagementRow row) {
        String ratingStatus = row.getRatingStatus();
        if (RatingStatus.APPLIED.name().equals(ratingStatus)) {
            return new RoomManagementDetail.Rating("반영 완료", TONE_GOOD,
                    "레이팅에 반영된 경기라 제외할 수 없습니다.", false, false);
        }
        if (RatingStatus.EXCLUDED.name().equals(ratingStatus)) {
            return new RoomManagementDetail.Rating("레이팅 제외됨", TONE_NEUTRAL,
                    "레이팅 반영에서 제외된 경기입니다.", false, false);
        }
        if (!RatingStatus.WAITING.name().equals(ratingStatus)) {
            return null;
        }
        if (row.getStartAt() == null || row.getStartAt().isAfter(LocalDateTime.now(clock))) {
            return new RoomManagementDetail.Rating("경기 전", TONE_NEUTRAL,
                    "경기가 시작된 뒤 제외할 수 있습니다.", true, false);
        }
        return new RoomManagementDetail.Rating("반영 대기", TONE_WARN, null, true, true);
    }

    /** 팀 배정 화면과 같은 기준: 매칭 완료·종료면 배정이 끝난 경기다. */
    private static boolean isTeamAssigned(String status) {
        return MatchStatus.MATCHED.dbValue().equals(status) || MatchStatus.CLOSED.dbValue().equals(status);
    }

    /* ---------------- 조회 조건 ---------------- */

    private static boolean hasManager(Long managerUserId) {
        return managerUserId != null && managerUserId > 0;
    }

    private RoomManagementQuery buildQuery(Long managerUserId, Long matchId, String q,
            String statusValue, Long sportId, Long facilityId) {
        return RoomManagementQuery.builder()
                .managerUserId(managerUserId)
                .matchId(matchId)
                .q(q)
                .statusValue(statusValue)
                .sportId(sportId)
                .facilityId(facilityId)
                .waitingApplicationStatus(ApplicationStatus.WAITING.dbValue())
                .individualType(ApplicationType.INDIVIDUAL.dbValue())
                .participationStatuses(ParticipationStatus.occupyingValues())
                .build();
    }

    /**
     * 화면에 되돌려 줄 검색어 정규화다. 공백은 조건 없음으로 만들고 과도한 길이는 자른다.
     * LIKE 이스케이프는 조회 파라미터를 만들 때만 적용해 필터에 이스케이프 문자가 새지 않게 한다.
     */
    static String normalizeQuery(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > QUERY_MAX_LENGTH ? trimmed.substring(0, QUERY_MAX_LENGTH) : trimmed;
    }

    /** LIKE 특수 문자만 이스케이프한다. SQL의 {@code ESCAPE '!'}와 짝을 이룬다. */
    static String escapeLikePattern(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == '!' || c == '%' || c == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    /** 담당 시설에 없는 종목은 '전체'로 되돌린다. */
    private static Long normalizeSportId(Long sportId, List<RoomManagementSport> sports) {
        if (sportId == null) {
            return null;
        }
        return sports.stream()
                .map(RoomManagementSport::getSportId)
                .filter(sportId::equals)
                .findFirst()
                .orElse(null);
    }

    /**
     * 담당자가 관리하는 활성 시설만 필터로 인정한다.
     *
     * <p>양수가 아닌 ID, 담당이 아닌 시설, 삭제된 시설, 없는 시설은 모두 '전체'(null)로
     * 되돌린다. 위조된 시설 ID가 목록 조건으로 흘러가지 않게 하는 지점이다.</p>
     */
    private Long normalizeFacilityId(Long managerUserId, Long facilityId) {
        if (facilityId == null || facilityId <= 0) {
            return null;
        }
        Long managed = roomManagementMapper.findManagedFacilityId(managerUserId, facilityId);
        return facilityId.equals(managed) ? facilityId : null;
    }

    /* ---------------- 화면 문구 ---------------- */

    private RoomManagementPage.Match toListMatch(RoomManagementRow row, LocalDateTime now) {
        return new RoomManagementPage.Match(
                row.getMatchId(),
                label(row.getTitle(), "제목 없음"),
                label(row.getFacilityName(), "시설 미설정"),
                label(row.getSportName(), "종목 미설정"),
                participationLabel(row),
                dateLabel(row),
                statusLabel(row.getStatus()),
                statusTone(row.getStatus()),
                editValues(row, now));
    }

    /**
     * 수정·취소 버튼에 실을 현재 값이다. RoomCommandService와 같은 기준(모집 중·매칭 완료이며
     * 시작 전)일 때만 만들고, 그 밖에는 null로 버튼을 숨긴다. 서버가 요청 때 다시 검증한다.
     */
    private static RoomManagementPage.Edit editValues(RoomManagementRow row, LocalDateTime now) {
        boolean editableStatus = MatchStatus.RECRUITING.dbValue().equals(row.getStatus())
                || MatchStatus.MATCHED.dbValue().equals(row.getStatus());
        if (!editableStatus || row.getStartAt() == null || row.getEndAt() == null
                || !row.getStartAt().isAfter(now)) {
            return null;
        }
        return new RoomManagementPage.Edit(
                row.getFacilityId(),
                row.getSportId(),
                row.getTitle() == null ? "" : row.getTitle().strip(),
                row.getStartAt().format(ISO_DATE),
                hourBlocks(row.getStartAt(), row.getEndAt()),
                // 예전 데이터에 두 팀 정원이 다른 방이 있으면 큰 쪽을 보여 줘 정원이 줄지 않게 한다.
                Math.max(0, Math.max(row.getTeamACapacity(), row.getTeamBCapacity())),
                row.getFeePerPerson() == null ? 0
                        : row.getFeePerPerson().setScale(0, RoundingMode.DOWN).intValue(),
                row.getRequiredSkill() == null ? "" : row.getRequiredSkill().strip(),
                Math.max(0, row.getReservedCount()),
                row.getPhotoUrl());
    }

    /** [시작, 종료) 구간이 덮는 1시간 블록 시작 시각 목록. "10:00,11:00" */
    private static String hourBlocks(LocalDateTime startAt, LocalDateTime endAt) {
        List<String> hours = new ArrayList<>();
        LocalDateTime cursor = startAt.withMinute(0).withSecond(0).withNano(0);
        while (cursor.isBefore(endAt) && hours.size() < 24) {
            hours.add(cursor.format(TIME));
            cursor = cursor.plusHours(1);
        }
        return String.join(",", hours);
    }

    private RoomManagementDetail.Application toApplication(RoomManagementApplicationRow row, boolean decisionOpen) {
        boolean club = ApplicationType.CLUB.dbValue().equals(row.getApplicationType());
        return new RoomManagementDetail.Application(
                row.getApplicationId(),
                label(row.getApplicantName(), "신청자 미상"),
                club && !isBlank(row.getClubName()) ? row.getClubName().strip() : null,
                kindLabel(row.getApplicationType()),
                applicationStatusLabel(row.getStatus()),
                createdAtLabel(row.getCreatedAt()),
                row.getApplicantUserId(),
                decisionOpen && row.getApplicationId() != null
                        && ApplicationStatus.WAITING.dbValue().equals(row.getStatus()));
    }

    private RoomManagementDetail.Participant toParticipant(RoomManagementParticipantRow row) {
        return new RoomManagementDetail.Participant(
                label(row.getNickname(), "이름 없음"),
                isBlank(row.getClubName()) ? null : row.getClubName().strip(),
                teamLabel(row.getMatchSide()),
                participationStatusLabel(row.getParticipationStatus()),
                row.getUserId());
    }

    private static String kindLabel(String applicationType) {
        if (ApplicationType.CLUB.dbValue().equals(applicationType)) {
            return "동호회 신청";
        }
        return "개인 신청";
    }

    private static String applicationStatusLabel(String status) {
        if (status == null) {
            return "상태 미설정";
        }
        return switch (status) {
            case "대기" -> "확정 대기";
            case "확정" -> "참가 확정";
            case "취소" -> "신청 취소";
            case "반려" -> "신청 반려";
            default -> status;
        };
    }

    /** 최근 요청의 상태 톤. 대기는 확인이 필요한 상태라 warn, 확정은 good, 반려는 bad다. */
    private static String applicationStatusTone(String status) {
        if (ApplicationStatus.WAITING.dbValue().equals(status)) {
            return TONE_WARN;
        }
        if (ApplicationStatus.CONFIRMED.dbValue().equals(status)) {
            return TONE_GOOD;
        }
        if (ApplicationStatus.REJECTED.dbValue().equals(status)) {
            return TONE_BAD;
        }
        return TONE_NEUTRAL;
    }

    private static String participationStatusLabel(String status) {
        if (ParticipationStatus.CONFIRMED.dbValue().equals(status)) {
            return "참가 확정";
        }
        if (ParticipationStatus.WAITING.dbValue().equals(status)) {
            return "확정 대기";
        }
        return status == null ? "상태 미설정" : status;
    }

    private static String teamLabel(String side) {
        if ("A".equals(side)) {
            return "A팀";
        }
        if ("B".equals(side)) {
            return "B팀";
        }
        return TEAM_UNASSIGNED;
    }

    private static String statusLabel(String status) {
        if (MatchStatus.RECRUITING.dbValue().equals(status)) {
            return "모집 중";
        }
        if (MatchStatus.MATCHED.dbValue().equals(status)) {
            // 상태 값이 매칭 완료일 뿐, A/B 팀 배정 완료를 뜻하지 않는다.
            return "매칭 완료";
        }
        if (MatchStatus.CLOSED.dbValue().equals(status)) {
            return "이용 완료";
        }
        if (MatchStatus.CANCELLED.dbValue().equals(status)) {
            return "취소";
        }
        return isBlank(status) ? "상태 미설정" : status;
    }

    private static String statusTone(String status) {
        if (MatchStatus.RECRUITING.dbValue().equals(status)) {
            return TONE_GOOD;
        }
        if (MatchStatus.MATCHED.dbValue().equals(status)) {
            return TONE_WARN;
        }
        if (MatchStatus.CANCELLED.dbValue().equals(status)) {
            return TONE_BAD;
        }
        return TONE_NEUTRAL;
    }

    /** "3 / 8명" */
    private static String participationLabel(RoomManagementRow row) {
        int max = Math.max(0, row.getMaxParticipants());
        int occupied = Math.max(0, row.getOccupiedCount());
        return occupied + " / " + max + "명";
    }

    /**
     * "2026-11-06(금) 18:00~20:00". 해가 바뀌거나 자정을 넘기면 종료 날짜까지 붙여
     * 어느 날 끝나는지 알 수 있게 한다.
     */
    private static String dateLabel(RoomManagementRow row) {
        if (row.getStartAt() == null) {
            return "일시 미설정";
        }
        LocalDateTime start = row.getStartAt();
        String head = start.format(DATE) + " " + start.format(TIME);
        LocalDateTime end = row.getEndAt();
        if (end == null) {
            return head;
        }
        boolean anotherDay = !end.toLocalDate().equals(start.toLocalDate());
        String tail = anotherDay
                ? end.format(DATE) + " " + end.format(TIME)
                : end.format(TIME);
        return head + "~" + tail;
    }

    private static String createdAtLabel(LocalDateTime createdAt) {
        return createdAt == null ? "접수 시각 미설정" : createdAt.format(DATE_TIME);
    }

    /* ---------------- 공통 ---------------- */

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String label(String value, String fallback) {
        return isBlank(value) ? fallback : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
