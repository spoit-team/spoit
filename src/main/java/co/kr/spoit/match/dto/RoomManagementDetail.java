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
    private final List<Application> applications;
    private final List<Participant> participants;

    public RoomManagementDetail(Long matchId, String title, String facilityName, String sportName,
            String dateLabel, String participation, String statusLabel, String statusTone,
            List<Application> applications, List<Participant> participants) {
        this.matchId = matchId;
        this.title = title;
        this.facilityName = facilityName;
        this.sportName = sportName;
        this.dateLabel = dateLabel;
        this.participation = participation;
        this.statusLabel = statusLabel;
        this.statusTone = statusTone;
        this.applications = List.copyOf(applications);
        this.participants = List.copyOf(participants);
    }

    /** 참가 신청 한 건 */
    @Getter
    public static class Application {

        private final String applicantName;
        private final String kindLabel;
        private final String statusLabel;
        private final String receiptNo;
        private final String createdAtLabel;

        public Application(String applicantName, String kindLabel, String statusLabel,
                String receiptNo, String createdAtLabel) {
            this.applicantName = applicantName;
            this.kindLabel = kindLabel;
            this.statusLabel = statusLabel;
            this.receiptNo = receiptNo;
            this.createdAtLabel = createdAtLabel;
        }
    }

    /** 참가자 한 명 */
    @Getter
    public static class Participant {

        private final String name;
        private final String teamLabel;
        private final String statusLabel;

        public Participant(String name, String teamLabel, String statusLabel) {
            this.name = name;
            this.teamLabel = teamLabel;
            this.statusLabel = statusLabel;
        }
    }
}
