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
        /** 무료 결제만 지원하므로 false */
        private final boolean canViewPaymentGuidance;
        private final String paymentGuidanceUrl;
    }
}
