package co.kr.spoit.club.dto;

import java.time.LocalDateTime;
import java.util.List;

import co.kr.spoit.mat.dto.ParticipantHistoryView;

// 동호회원 동호회 이력: MAT ParticipantHistoryView에서 신고·제재 빼고 마지막 동호회 경기일 추가
public record ClubMemberHistoryView(
        String nickname,
        String tierLabel,
        int ratedMatchCount,
        List<ParticipantHistoryView.RecentChange> recentChanges,
        int excludedMatchCount,
        LocalDateTime lastClubMatchAt) {
}
