package co.kr.spoit.match.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** RES-03·04 참가 신청 화면(matches/join.html) */
@Getter
@Builder
public class JoinPage {

    private final JoinMatch match;
    private final Selection selection;
    private final boolean canReserve;
    private final String capacityNotice;
    private final String feeTotalText;
    private final String feeSummaryNote;
    /** 완료 화면에서만 채운다 */
    private final Completion completion;

    @Getter
    @Builder
    public static class JoinMatch {
        private final Long matchId;
        private final String title;
        private final String sportName;
        private final String statusLabel;
        private final String facilityName;
        private final String dateLabel;
        private final String time;
        private final String place;
        private final String levelLabel;
        private final String feeText;
        private final String capacityLabel;
        private final int maxParticipants;
        private final int occupiedSeats;
        private final int occupancyPercent;
        private final String remainingLabel;
    }

    @Getter
    @AllArgsConstructor
    public static class Selection {
        private final String kindLabel;
        private final String applicantName;
        private final String applicantPhone;
        /** individual 또는 team (템플릿 비교값) */
        private final String applicationType;
        private final int seats;
        private final List<Person> participants;
        /** RES-04 팀 신청이면 선택한 동호회, 개인 신청이면 null (신청 폼 hidden 값) */
        private final Long clubId;
        /** RES-04 팀 신청이면 함께 신청할 동호회원(club_member_id), 개인 신청이면 빈 목록 */
        private final List<Long> clubMemberIds;
    }

    @Getter
    @AllArgsConstructor
    public static class Person {
        private final String nickname;
        private final String roleLabel;
    }

    @Getter
    @Builder
    public static class Completion {
        private final String title;
        private final String message;
        private final String roomTitle;
        private final String applicantText;
        private final String participantText;
        private final String receiptNo;
        private final String stateLabel;
        private final String paymentGuidance;
        private final String totalText;
        private final boolean teamAssigned;
        private final int teamACount;
        private final int teamBCount;
    }
}
