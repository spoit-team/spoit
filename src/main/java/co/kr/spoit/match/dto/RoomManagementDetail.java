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

    public RoomManagementDetail(Long matchId, String title, String facilityName, String sportName,
            String dateLabel, String participation, String statusLabel, String statusTone,
            boolean teamAssigned, List<Application> applications, List<Participant> participants) {
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
