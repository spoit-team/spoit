package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.domain.ActivePenalty;
import co.kr.spoit.mat.domain.AssignmentMatch;
import co.kr.spoit.mat.domain.RecentRatingChange;
import co.kr.spoit.mat.domain.SportRating;
import co.kr.spoit.mat.dto.ParticipantHistoryView;
import co.kr.spoit.mat.mapper.MatchAssignmentMapper;
import co.kr.spoit.mat.mapper.ParticipantHistoryMapper;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ParticipantHistoryServiceTest {

    private static final long MATCH_ID = 10L;
    private static final long FACILITY_ID = 3L;
    private static final long SPORT_ID = 2L;
    private static final long MANAGER_ID = 7L;
    private static final long USER_ID = 21L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    @Mock
    private MatchAssignmentMapper assignmentMapper;

    @Mock
    private ParticipantHistoryMapper historyMapper;

    @Mock
    private SportRatingMapper ratingMapper;

    private ParticipantHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ParticipantHistoryService(assignmentMapper, historyMapper, ratingMapper);
    }

    @Test
    void deniesManagerOfAnotherFacilityBeforeReadingHistory() {
        when(assignmentMapper.findAssignmentMatch(MATCH_ID)).thenReturn(Optional.of(match()));
        when(assignmentMapper.isFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getHistory(MATCH_ID, USER_ID, MANAGER_ID, NOW))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(historyMapper, ratingMapper);
    }

    @Test
    void returnsEmptyForMissingMatch() {
        when(assignmentMapper.findAssignmentMatch(MATCH_ID)).thenReturn(Optional.empty());

        assertThat(service.getHistory(MATCH_ID, USER_ID, MANAGER_ID, NOW)).isEmpty();
        verify(assignmentMapper, never()).isFacilityManager(anyLong(), anyLong());
        verifyNoInteractions(historyMapper, ratingMapper);
    }

    @Test
    void returnsEmptyForMemberWithoutApplicationOrParticipationInMatch() {
        givenManagedMatch();
        when(historyMapper.findNicknameInMatch(MATCH_ID, USER_ID)).thenReturn(Optional.empty());

        assertThat(service.getHistory(MATCH_ID, USER_ID, MANAGER_ID, NOW)).isEmpty();
        verify(historyMapper, never()).countReports(anyLong(), anyString());
        verifyNoInteractions(ratingMapper);
    }

    @Test
    void summarizesRatingOfMatchSportWithCountsAndActivePenalties() {
        givenManagedMatch();
        when(historyMapper.findNicknameInMatch(MATCH_ID, USER_ID)).thenReturn(Optional.of("참가자"));
        when(ratingMapper.findByUserIdAndSportId(USER_ID, SPORT_ID)).thenReturn(Optional.of(rating("1032.00", 7)));
        when(historyMapper.findRecentChanges(USER_ID, SPORT_ID, 5)).thenReturn(List.of(
                change("2026-10-26T13:00", "-3.10"), change("2026-10-25T13:00", "7.69")));
        when(historyMapper.countExcludedMatches(USER_ID, "확정", "EXCLUDED", "매칭 완료", "종료")).thenReturn(1);
        when(historyMapper.countReports(USER_ID, "처리 완료")).thenReturn(2);
        when(historyMapper.findActivePenalties(USER_ID, "적용 중", NOW)).thenReturn(List.of(
                penalty("이용 제한", LocalDateTime.of(2026, 11, 10, 9, 0)), penalty("경고", null)));

        ParticipantHistoryView view = service.getHistory(MATCH_ID, USER_ID, MANAGER_ID, NOW).orElseThrow();

        assertThat(view.nickname()).isEqualTo("참가자");
        assertThat(view.tierLabel()).isEqualTo("실버 4 · 64P");
        assertThat(view.ratedMatchCount()).isEqualTo(7);
        assertThat(view.recentChanges())
                .extracting(ParticipantHistoryView.RecentChange::calculatedAt,
                        ParticipantHistoryView.RecentChange::finalDelta)
                .containsExactly(tuple(LocalDateTime.of(2026, 10, 26, 13, 0), new BigDecimal("-3.10")),
                        tuple(LocalDateTime.of(2026, 10, 25, 13, 0), new BigDecimal("7.69")));
        assertThat(view.excludedMatchCount()).isEqualTo(1);
        assertThat(view.confirmedReportCount()).isEqualTo(2);
        assertThat(view.activePenalties())
                .extracting(ParticipantHistoryView.Penalty::penaltyType, ParticipantHistoryView.Penalty::endAt)
                .containsExactly(tuple("이용 제한", LocalDateTime.of(2026, 11, 10, 9, 0)), tuple("경고", null));
    }

    @Test
    void showsNoRecordForMemberWithoutRatingOrHistory() {
        givenManagedMatch();
        when(historyMapper.findNicknameInMatch(MATCH_ID, USER_ID)).thenReturn(Optional.of("새 회원"));
        when(ratingMapper.findByUserIdAndSportId(USER_ID, SPORT_ID)).thenReturn(Optional.empty());

        ParticipantHistoryView view = service.getHistory(MATCH_ID, USER_ID, MANAGER_ID, NOW).orElseThrow();

        assertThat(view.tierLabel()).isEqualTo("기록 없음");
        assertThat(view.ratedMatchCount()).isZero();
        assertThat(view.recentChanges()).isEmpty();
        assertThat(view.excludedMatchCount()).isZero();
        assertThat(view.confirmedReportCount()).isZero();
        assertThat(view.activePenalties()).isEmpty();
    }

    private void givenManagedMatch() {
        when(assignmentMapper.findAssignmentMatch(MATCH_ID)).thenReturn(Optional.of(match()));
        when(assignmentMapper.isFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(true);
    }

    private static AssignmentMatch match() {
        var match = new AssignmentMatch();
        match.setMatchId(MATCH_ID);
        match.setFacilityId(FACILITY_ID);
        match.setSportId(SPORT_ID);
        return match;
    }

    private static SportRating rating(String mmr, int ratedMatchCount) {
        var rating = new SportRating();
        rating.setUserId(USER_ID);
        rating.setSportId(SPORT_ID);
        rating.setCurrentMmr(new BigDecimal(mmr));
        rating.setRatedMatchCount(ratedMatchCount);
        return rating;
    }

    private static RecentRatingChange change(String calculatedAt, String finalDelta) {
        var change = new RecentRatingChange();
        change.setCalculatedAt(LocalDateTime.parse(calculatedAt));
        change.setFinalDelta(new BigDecimal(finalDelta));
        return change;
    }

    private static ActivePenalty penalty(String penaltyType, LocalDateTime endAt) {
        var penalty = new ActivePenalty();
        penalty.setPenaltyType(penaltyType);
        penalty.setEndAt(endAt);
        return penalty;
    }
}
