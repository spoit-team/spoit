package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.domain.AssignmentMatch;
import co.kr.spoit.mat.domain.MatchParticipant;
import co.kr.spoit.mat.dto.AssignmentResult;
import co.kr.spoit.mat.dto.AssignmentResult.Outcome;
import co.kr.spoit.mat.mapper.MatchAssignmentMapper;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AutoAssignmentServiceTest {

    private static final long MATCH_ID = 10L;
    private static final long FACILITY_ID = 3L;
    private static final long MANAGER_ID = 7L;
    private static final LocalDateTime TOMORROW = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1);

    @Mock
    private MatchAssignmentMapper assignmentMapper;

    @Mock
    private MatchParticipantMapper participantMapper;

    private AutoAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new AutoAssignmentService(assignmentMapper, participantMapper,
                new AutoTeamAssigner(new TeamCaptainSelector()));
    }

    @Test
    void assignsOnlyConfirmedParticipantsWhenCountMatchesCapacity() {
        givenManagedMatch(match("모집 중", 2, 2, TOMORROW));
        // 취소 행(5)에 예전 B 배정이 남아 있어도 배정 완료로 보지 않는다.
        var cancelled = participant(5L, "취소", "1000");
        cancelled.setMatchSide("B");
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(List.of(
                participant(1L, "확정", "1200"),
                participant(2L, "확정", "900"),
                participant(3L, "대기", "1500"),
                participant(4L, "확정", "1100"),
                cancelled,
                participant(6L, "확정", "950"),
                participant(7L, "반려", "800")));

        AssignmentResult result = service.preview(MATCH_ID, MANAGER_ID);

        assertThat(result.outcome()).isEqualTo(Outcome.READY);
        assertThat(result.assignment().teamAParticipantIds()).containsExactly(1L, 2L);
        assertThat(result.assignment().teamBParticipantIds()).containsExactly(4L, 6L);
        assertThat(result.assignment().mmrDifference()).isEqualTo(new BigDecimal("50.00"));
        assertThat(result.assignment().captainAParticipantId()).isEqualTo(1L);
        assertThat(result.assignment().captainBParticipantId()).isEqualTo(4L);
    }

    @Test
    void returnsNotEnoughWhenConfirmedParticipantsAreShort() {
        givenManagedMatch(match("모집 중", 2, 2, TOMORROW));
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(List.of(
                participant(1L, "확정", "1000"),
                participant(2L, "확정", "1000"),
                participant(3L, "확정", "1000"),
                participant(4L, "대기", "1000")));

        AssignmentResult result = service.preview(MATCH_ID, MANAGER_ID);

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_ENOUGH_PARTICIPANTS);
        assertThat(result.message()).contains("4명", "현재 3명");
        assertThat(result.assignment()).isNull();
    }

    @Test
    void treatsMoreConfirmedParticipantsThanCapacityAsDataError() {
        givenManagedMatch(match("모집 중", 2, 2, TOMORROW));
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(confirmedParticipants(5));

        assertThatIllegalStateException()
                .isThrownBy(() -> service.preview(MATCH_ID, MANAGER_ID))
                .withMessageContaining("정원=4")
                .withMessageContaining("확정=5");
    }

    @Test
    void rejectsRequestAfterMatchStarted() {
        givenManagedMatch(match("모집 중", 2, 2, LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1)));

        AssignmentResult result = service.preview(MATCH_ID, MANAGER_ID);

        assertThat(result.outcome()).isEqualTo(Outcome.STARTED);
        verifyNoInteractions(participantMapper);
    }

    @Test
    void treatsMatchedStatusAsAlreadyAssigned() {
        givenManagedMatch(match("매칭 완료", 2, 2, TOMORROW));
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(confirmedParticipants(4));

        assertThat(service.preview(MATCH_ID, MANAGER_ID).outcome()).isEqualTo(Outcome.ALREADY_ASSIGNED);
    }

    @Test
    void treatsConfirmedAssignmentTimeAsAlreadyAssigned() {
        var match = match("모집 중", 2, 2, TOMORROW);
        match.setAssignmentConfirmedAt(LocalDateTime.of(2026, 10, 26, 18, 0));
        givenManagedMatch(match);
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(confirmedParticipants(4));

        assertThat(service.preview(MATCH_ID, MANAGER_ID).outcome()).isEqualTo(Outcome.ALREADY_ASSIGNED);
    }

    @Test
    void treatsConfirmedParticipantWithSideAsAlreadyAssigned() {
        givenManagedMatch(match("모집 중", 2, 2, TOMORROW));
        var participants = confirmedParticipants(4);
        participants.get(2).setMatchSide("A");
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(participants);

        assertThat(service.preview(MATCH_ID, MANAGER_ID).outcome()).isEqualTo(Outcome.ALREADY_ASSIGNED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"취소", "종료"})
    void rejectsMatchThatIsNotRecruiting(String status) {
        givenManagedMatch(match(status, 2, 2, TOMORROW));
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(confirmedParticipants(4));

        assertThat(service.preview(MATCH_ID, MANAGER_ID).outcome()).isEqualTo(Outcome.NOT_RECRUITING);
    }

    @ParameterizedTest
    @CsvSource({"3, 2", "0, 0", "12, 12"})
    void rejectsUnequalOrUnsupportedTeamCapacity(int teamA, int teamB) {
        givenManagedMatch(match("모집 중", teamA, teamB, TOMORROW));
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(confirmedParticipants(teamA + teamB));

        AssignmentResult result = service.preview(MATCH_ID, MANAGER_ID);

        assertThat(result.outcome()).isEqualTo(Outcome.UNSUPPORTED_CAPACITY);
        assertThat(result.assignment()).isNull();
    }

    @Test
    void treatsInvalidEntryMmrAsDataError() {
        givenManagedMatch(match("모집 중", 1, 1, TOMORROW));
        var broken = new MatchParticipant();
        broken.setParticipantId(2L);
        broken.setParticipationStatus("확정");
        broken.setEntryMmr(new BigDecimal("-1.00"));
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(List.of(participant(1L, "확정", "1000"), broken));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.preview(MATCH_ID, MANAGER_ID))
                .withMessageContaining("participantId=2");
    }

    @Test
    void returnsNotFoundWithoutCheckingManager() {
        when(assignmentMapper.findAssignmentMatch(MATCH_ID)).thenReturn(Optional.empty());

        assertThat(service.preview(MATCH_ID, MANAGER_ID).outcome()).isEqualTo(Outcome.NOT_FOUND);
        verify(assignmentMapper, never()).isFacilityManager(anyLong(), anyLong());
        verifyNoInteractions(participantMapper);
    }

    @Test
    void deniesManagerOfAnotherFacility() {
        when(assignmentMapper.findAssignmentMatch(MATCH_ID))
                .thenReturn(Optional.of(match("모집 중", 2, 2, TOMORROW)));
        when(assignmentMapper.isFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.preview(MATCH_ID, MANAGER_ID))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(participantMapper);
    }

    private void givenManagedMatch(AssignmentMatch match) {
        when(assignmentMapper.findAssignmentMatch(MATCH_ID)).thenReturn(Optional.of(match));
        when(assignmentMapper.isFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(true);
    }

    private AssignmentMatch match(String status, int teamA, int teamB, LocalDateTime startAt) {
        var match = new AssignmentMatch();
        match.setMatchId(MATCH_ID);
        match.setFacilityId(FACILITY_ID);
        match.setStatus(status);
        match.setTeamACapacity(teamA);
        match.setTeamBCapacity(teamB);
        match.setStartAt(startAt);
        return match;
    }

    private List<MatchParticipant> confirmedParticipants(int count) {
        List<MatchParticipant> participants = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            participants.add(participant(id, "확정", "1000"));
        }
        return participants;
    }

    private MatchParticipant participant(Long participantId, String status, String mmr) {
        var participant = new MatchParticipant();
        participant.setParticipantId(participantId);
        participant.setMatchId(MATCH_ID);
        participant.setParticipationStatus(status);
        participant.setEntryMmr(new BigDecimal(mmr));
        return participant;
    }
}
