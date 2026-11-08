package co.kr.spoit.match.dto;

import java.util.List;

import lombok.Getter;

/**
 * 시설 관리자 매칭방 상세(운영) 화면 모델이다.
 *
 * <p>조회 권한이 없거나 방이 없으면 서비스가 {@code null}을 돌려주며 이 객체를 만들지 않는다.</p>
 */
@Getter
public class RoomManagementDetail {

    private final Long matchId;
    private final String title;
    private final String facilityName;
    private final String sportName;
    /** 날짜와 시작~종료 시각(자정을 넘기면 종료 날짜까지) */
    private final String dateLabel;
    /** "3 / 8명" */
    private final String participation;
    private final String statusLabel;
    private final String statusTone;
    /** 팀 배정이 끝났으면 true. 확정 신청은 신청 내역 대신 참가자 표에 팀과 함께 보인다. */
    private final boolean teamAssigned;
    private final List<Application> applications;
    private final List<Participant> participants;
    /** 레이팅 반영 섹션. 팀 배정 전·취소된 방처럼 레이팅과 무관하면 null이고 섹션을 숨긴다. */
    private final Rating rating;

    public RoomManagementDetail(Long matchId, String title, String facilityName, String sportName,
            String dateLabel, String participation, String statusLabel, String statusTone,
            boolean teamAssigned, List<Application> applications, List<Participant> participants) {
        this(matchId, title, facilityName, sportName, dateLabel, participation, statusLabel, statusTone,
                teamAssigned, applications, participants, null);
    }

    public RoomManagementDetail(Long matchId, String title, String facilityName, String sportName,
            String dateLabel, String participation, String statusLabel, String statusTone,
            boolean teamAssigned, List<Application> applications, List<Participant> participants,
            Rating rating) {
        this.matchId = matchId;
        this.title = title;
        this.facilityName = facilityName;
        this.sportName = sportName;
        this.dateLabel = dateLabel;
        this.participation = participation;
        this.statusLabel = statusLabel;
        this.statusTone = statusTone;
        this.teamAssigned = teamAssigned;
        this.applications = List.copyOf(applications);
        this.participants = List.copyOf(participants);
        this.rating = rating;
    }

    /**
     * 레이팅 반영 상태와 노쇼 경기 레이팅 제외 가능 여부.
     *
     * <p>{@code excludable}은 화면 버튼 표시용이며, 실제 제외 가능 여부는 레이팅 제외 API가 잠금 아래에서 다시 확인한다.</p>
     */
    @Getter
    public static class Rating {

        private final String label;
        private final String tone;
        private final String message;
        /** 아직 반영·제외 전이면 true. 제외 버튼을 보여 주고, 시작 전이면 비활성으로 둔다. */
        private final boolean waiting;
        /** 경기가 시작됐고 레이팅 반영 전이라 제외 버튼을 누를 수 있으면 true */
        private final boolean excludable;

        public Rating(String label, String tone, String message, boolean waiting, boolean excludable) {
            this.label = label;
            this.tone = tone;
            this.message = message;
            this.waiting = waiting;
            this.excludable = excludable;
        }
    }

    /** 참가 신청 한 건 (동호회 신청은 팀원마다 한 건) */
    @Getter
    public static class Application {

        private final Long applicationId;
        /** 신청 회원 닉네임 */
        private final String applicantName;
        /** 동호회 신청이면 동호회명, 개인 신청이면 null */
        private final String clubName;
        private final String kindLabel;
        private final String statusLabel;
        private final String createdAtLabel;
        /** 참가자 이력 모달을 열 회원 ID */
        private final Long historyUserId;
        /** 확정 대기이고 모집 중인 경기라 승인·반려할 수 있으면 true */
        private final boolean decidable;

        public Application(Long applicationId, String applicantName, String clubName, String kindLabel,
                String statusLabel, String createdAtLabel, Long historyUserId, boolean decidable) {
            this.applicationId = applicationId;
            this.applicantName = applicantName;
            this.clubName = clubName;
            this.kindLabel = kindLabel;
            this.statusLabel = statusLabel;
            this.createdAtLabel = createdAtLabel;
            this.historyUserId = historyUserId;
            this.decidable = decidable;
        }
    }

    /** 참가자 한 명 */
    @Getter
    public static class Participant {

        private final String name;
        /** 동호회 신청으로 들어온 참가자면 동호회명, 아니면 null */
        private final String clubName;
        private final String teamLabel;
        private final String statusLabel;
        /** 참가자 이력 모달을 열 회원 ID */
        private final Long historyUserId;

        public Participant(String name, String clubName, String teamLabel, String statusLabel, Long historyUserId) {
            this.name = name;
            this.clubName = clubName;
            this.teamLabel = teamLabel;
            this.statusLabel = statusLabel;
            this.historyUserId = historyUserId;
        }
    }
}
