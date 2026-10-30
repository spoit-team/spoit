package co.kr.spoit.match.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.ApplicationType;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.match.domain.RoomStatusFilter;
import co.kr.spoit.match.dto.RoomManagementApplicationRow;
import co.kr.spoit.match.dto.RoomManagementDetail;
import co.kr.spoit.match.dto.RoomManagementPage;
import co.kr.spoit.match.dto.RoomManagementParticipantRow;
import co.kr.spoit.match.dto.RoomManagementQuery;
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

    private final RoomManagementMapper roomManagementMapper;

    public RoomManagementService(RoomManagementMapper roomManagementMapper) {
        this.roomManagementMapper = roomManagementMapper;
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

        List<RoomManagementPage.Match> matches = new ArrayList<>();
        RoomManagementQuery query = buildQuery(managerUserId, null, escapeLikePattern(normalizedQ),
                statusFilter.dbValue(), normalizedSportId, normalizedFacilityId);
        for (RoomManagementRow row : nullSafe(roomManagementMapper.findRooms(query))) {
            if (row != null) {
                matches.add(toListMatch(row));
            }
        }

        List<RoomManagementPage.Sport> sportItems = sports.stream()
                .map(sport -> new RoomManagementPage.Sport(sport.getSportId(), sport.getName()))
                .toList();

        return RoomManagementPage.of(matches, sportItems,
                new RoomManagementPage.Filters(normalizedQ, statusFilter.value(), normalizedSportId,
                        normalizedFacilityId));
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

        List<RoomManagementDetail.Application> applications = new ArrayList<>();
        for (RoomManagementApplicationRow application : nullSafe(roomManagementMapper.findApplications(query))) {
            if (application != null) {
                applications.add(toApplication(application));
            }
        }

        List<RoomManagementDetail.Participant> participants = new ArrayList<>();
        for (RoomManagementParticipantRow participant : nullSafe(roomManagementMapper.findParticipants(query))) {
            if (participant != null) {
                participants.add(toParticipant(participant));
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
                applications,
                participants);
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

    private RoomManagementPage.Match toListMatch(RoomManagementRow row) {
        return new RoomManagementPage.Match(
                row.getMatchId(),
                label(row.getTitle(), "제목 없음"),
                label(row.getFacilityName(), "시설 미설정"),
                label(row.getSportName(), "종목 미설정"),
                participationLabel(row),
                dateLabel(row),
                statusLabel(row.getStatus()),
                statusTone(row.getStatus()));
    }

    private RoomManagementDetail.Application toApplication(RoomManagementApplicationRow row) {
        return new RoomManagementDetail.Application(
                applicantName(row),
                kindLabel(row.getApplicationType()),
                applicationStatusLabel(row.getStatus()),
                row.getReceiptNo() == null ? "" : row.getReceiptNo(),
                createdAtLabel(row.getCreatedAt()));
    }

    private RoomManagementDetail.Participant toParticipant(RoomManagementParticipantRow row) {
        return new RoomManagementDetail.Participant(
                label(row.getNickname(), "이름 없음"),
                teamLabel(row.getMatchSide()),
                participationStatusLabel(row.getParticipationStatus()));
    }

    /** 동호회 신청은 신청자(동호회장) 대신 동호회명을 우선 보여 준다. */
    private String applicantName(RoomManagementApplicationRow row) {
        if (ApplicationType.CLUB.dbValue().equals(row.getApplicationType()) && !isBlank(row.getClubName())) {
            return row.getClubName().strip();
        }
        return label(row.getApplicantName(), "신청자 미상");
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
        if (ApplicationStatus.WAITING.dbValue().equals(status)) {
            return "확정 대기";
        }
        if (ApplicationStatus.CONFIRMED.dbValue().equals(status)) {
            return "참가 확정";
        }
        if (ApplicationStatus.CANCELLED.dbValue().equals(status)) {
            return "신청 취소";
        }
        if (ApplicationStatus.REJECTED.dbValue().equals(status)) {
            return "신청 반려";
        }
        return status;
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
