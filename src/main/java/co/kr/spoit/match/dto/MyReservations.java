package co.kr.spoit.match.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** RES-06 마이페이지 신청 내역 (mypage/index.html #reservations, 요약 집계) */
@Getter
@AllArgsConstructor
public class MyReservations {

    private final List<Entry> current;
    private final List<Entry> past;
    /** 참가 확정 (예정 + 완료) */
    private final int confirmedCount;
    private final int completedCount;
    private final int waitingCount;

    public static MyReservations empty() {
        return new MyReservations(List.of(), List.of(), 0, 0, 0);
    }

    /** 템플릿 entry.* 속성 */
    @Getter
    @AllArgsConstructor
    public static class Entry {
        private final Long applicationId;
        private final Long matchId;
        private final String sportName;
        private final String typeLabel;
        private final String stateLabel;
        private final String stateClass;
        private final String title;
        private final String metaLine;
        private final String seatLine;
        private final String statusNote;
        /** 신청자 본인 + 모집 중 + 경기 시작 전일 때만 true */
        private final boolean canCancel;
        private final String cancelLabel;
        /** 결제 안내 링크 (쓰지 않음, 결제는 신청 화면에서 끝난다) */
        private final boolean canViewPaymentGuidance;
        private final String paymentGuidanceUrl;
        /** RES-04 팀 신청 대표의 취소: 누르면 취소할 팀원을 고르는 확인창을 먼저 띄운다 */
        private final boolean teamCancel;
        /** 확인창 팀원 목록: 아직 취소되지 않은 묶음 행 (팀 신청 취소일 때만, 아니면 빈 목록) */
        private final List<TeamMemberOption> teamMembers;
        /** 결제한 회원에게만 보이는 결제·환불 요약 ("카카오페이 45,000원 결제 · 18,000원 환불"), 없으면 null */
        private final String paymentLine;
        /** 취소하면 한 명당 환불되는 금액(원). 결제가 없거나 취소할 수 없으면 0 */
        private final long refundUnitAmount;
    }

    /** 팀 신청 취소 확인창의 팀원 한 명 */
    @Getter
    @AllArgsConstructor
    public static class TeamMemberOption {
        private final Long applicationId;
        private final String nickname;
        private final String stateLabel;
        private final boolean leader;
    }
}
