package co.kr.spoit.mat.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 시설 관리자에게 보여 줄 참가자 이력 요약. 신고 내용·신고자·제재 사유 같은 상세는 담지 않는다. */
public record ParticipantHistoryView(
        String nickname,
        String tierLabel,
        int ratedMatchCount,
        List<RecentChange> recentChanges,
        int excludedMatchCount,
        int confirmedReportCount,
        List<Penalty> activePenalties) {

    public record RecentChange(LocalDateTime calculatedAt, BigDecimal finalDelta) {
    }

    public record Penalty(String penaltyType, LocalDateTime endAt) {
    }
}
