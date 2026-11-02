package co.kr.spoit.match.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

/**
 * 시설 관리자 매칭방 목록 화면 모델이다.
 *
 * <p>목록은 항상 null이 아닌 리스트이며, 필터 선택 상태는 정규화된 값을 그대로 돌려준다.
 * 담당 시설이나 매칭방이 없으면 목록은 비어 있고, 종목 선택지는 담당 시설이 제공하는
 * 종목에서 나오므로 비어 있지 않을 수 있다.</p>
 */
@Getter
public class RoomManagementPage {

    /** 생성·수정 모달의 1시간 블록 시작 시각 범위. 운영 시간 판단은 서버가 다시 한다. */
    static final int FIRST_HOUR = 6;
    static final int LAST_HOUR = 23;

    private static final List<HourSlot> HOUR_SLOTS = buildHourSlots();

    private final List<Match> matches;
    private final List<Sport> sports;
    private final Filters filters;
    /** 생성·수정 모달의 종목 선택지. 시설마다 제공 종목이 달라 시설 ID와 함께 준다. */
    private final List<FacilitySport> facilitySports;

    private RoomManagementPage(List<Match> matches, List<Sport> sports, Filters filters,
            List<FacilitySport> facilitySports) {
        this.matches = List.copyOf(matches);
        this.sports = List.copyOf(sports);
        this.filters = filters;
        this.facilitySports = List.copyOf(facilitySports);
    }

    /** 담당 시설이 없거나 관리자 식별자가 없을 때 쓰는 빈 페이지 */
    public static RoomManagementPage empty() {
        return new RoomManagementPage(List.of(), List.of(), Filters.none(), List.of());
    }

    public static RoomManagementPage of(List<Match> matches, List<Sport> sports, Filters filters) {
        return new RoomManagementPage(matches, sports, filters, List.of());
    }

    public static RoomManagementPage of(List<Match> matches, List<Sport> sports, Filters filters,
            List<FacilitySport> facilitySports) {
        return new RoomManagementPage(matches, sports, filters, facilitySports);
    }

    /** 생성·수정 모달의 1시간 블록. 담당 시설·날짜와 무관한 고정 목록이다. */
    public List<HourSlot> getHourSlots() {
        return HOUR_SLOTS;
    }

    private static List<HourSlot> buildHourSlots() {
        List<HourSlot> slots = new ArrayList<>();
        for (int hour = FIRST_HOUR; hour <= LAST_HOUR; hour++) {
            slots.add(new HourSlot(String.format("%02d:00", hour)));
        }
        return Collections.unmodifiableList(slots);
    }

    /** 목록 표 한 행 */
    @Getter
    public static class Match {

        private final Long matchId;
        private final String title;
        private final String facilityName;
        private final String sportName;
        /** "3 / 8명" */
        private final String participation;
        /** "10/29(수) 18:00~20:00" (자정을 넘기면 종료 일시까지 표시) */
        private final String dateLabel;
        private final String statusLabel;
        private final String statusTone;
        /** 수정·취소할 수 있는 방의 현재 값. 종료·취소·시작한 방이면 null */
        private final Edit edit;

        public Match(Long matchId, String title, String facilityName, String sportName,
                String participation, String dateLabel, String statusLabel, String statusTone) {
            this(matchId, title, facilityName, sportName, participation, dateLabel, statusLabel, statusTone, null);
        }

        public Match(Long matchId, String title, String facilityName, String sportName,
                String participation, String dateLabel, String statusLabel, String statusTone, Edit edit) {
            this.matchId = matchId;
            this.title = title;
            this.facilityName = facilityName;
            this.sportName = sportName;
            this.participation = participation;
            this.dateLabel = dateLabel;
            this.statusLabel = statusLabel;
            this.statusTone = statusTone;
            this.edit = edit;
        }
    }

    /**
     * 수정 모달을 채울 현재 값이다. {@code scheduleLocked}이면 시설·종목·날짜·시간을 바꿀 수 없다.
     * {@code hours}는 선택된 1시간 블록 시작 시각의 쉼표 목록("10:00,11:00")이다.
     */
    @Getter
    public static class Edit {

        private final Long facilityId;
        private final Long sportId;
        private final String title;
        /** yyyy-MM-dd */
        private final String date;
        private final String hours;
        /** 팀당 정원 */
        private final int teamCapacity;
        private final int feePerPerson;
        private final String requiredSkill;
        /** 참가자 행(대기·확정) + 대기 신청 인원 */
        private final int reservedCount;

        public Edit(Long facilityId, Long sportId, String title, String date, String hours,
                int teamCapacity, int feePerPerson,
                String requiredSkill, int reservedCount) {
            this.facilityId = facilityId;
            this.sportId = sportId;
            this.title = title;
            this.date = date;
            this.hours = hours;
            this.teamCapacity = teamCapacity;
            this.feePerPerson = feePerPerson;
            this.requiredSkill = requiredSkill;
            this.reservedCount = reservedCount;
        }

        public boolean isScheduleLocked() {
            return reservedCount > 0;
        }
    }

    /** 생성·수정 모달의 종목 선택지 한 건 */
    @Getter
    public static class FacilitySport {

        private final Long facilityId;
        private final Long sportId;
        private final String name;

        public FacilitySport(Long facilityId, Long sportId, String name) {
            this.facilityId = facilityId;
            this.sportId = sportId;
            this.name = name;
        }
    }

    /** 생성·수정 모달의 1시간 블록. 템플릿 계약상 disabled·label을 함께 준다. */
    @Getter
    public static class HourSlot {

        private final String time;
        private final boolean disabled;
        private final String label;

        HourSlot(String time) {
            this.time = time;
            this.disabled = false;
            this.label = null;
        }
    }

    /** 종목 필터 선택지 */
    @Getter
    public static class Sport {

        private final Long sportId;
        private final String name;

        public Sport(Long sportId, String name) {
            this.sportId = sportId;
            this.name = name;
        }
    }

    /** 정규화된 필터 선택 상태. status는 all/recruiting/matched/completed/cancelled 중 하나다. */
    @Getter
    public static class Filters {

        private final String q;
        private final String status;
        private final Long sportId;
        /** 담당 시설 필터. '전체'면 null */
        private final Long facilityId;

        public Filters(String q, String status, Long sportId, Long facilityId) {
            this.q = q;
            this.status = status;
            this.sportId = sportId;
            this.facilityId = facilityId;
        }

        public static Filters none() {
            return new Filters(null, "all", null, null);
        }
    }
}
