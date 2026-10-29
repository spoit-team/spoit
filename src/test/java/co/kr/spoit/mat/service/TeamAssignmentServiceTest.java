package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TeamAssignmentServiceTest {

    private static final long MATCH_ID = 10L;
    private static final long FACILITY_ID = 3L;
    private static final long MANAGER_ID = 7L;
    private static final LocalDateTime TOMORROW = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1);

    @Mock
    private MatchAssignmentMapper assignmentMapper;

    @Mock
    private MatchParticipantMapper participantMapper;

    private TeamAssignmentService service;

    @BeforeEach
    void setUp() {
        TeamCaptainSelector captainSelector = new TeamCaptainSelector();
        AutoAssignmentService autoAssignmentService = new AutoAssignmentService(assignmentMapper, participantMapper,
                new AutoTeamAssigner(captainSelector));
        service = new TeamAssignmentService(assignmentMapper, participantMapper, autoAssignmentService, captainSelector);
    }

    @Test
    void confirmsManualTeamsWithServerSelectedCaptains() {
        givenLockedMatch(match("모집 중", TOMORROW));
        givenParticipants();
        when(assignmentMapper.updateMatchSide(eq(MATCH_ID), any(), anyString(), eq("확정"))).thenReturn(2);
        when(assignmentMapper.markAssigned(eq(MATCH_ID), anyString(), any(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(1);

        AssignmentResult result = service.confirmManual(MATCH_ID, MANAGER_ID, List.of(2L, 4L), List.of(1L, 6L));

        assertThat(result.outcome()).isEqualTo(Outcome.ASSIGNED);
        verify(assignmentMapper).updateMatchSide(MATCH_ID, List.of(2L, 4L), "A", "확정");
        verify(assignmentMapper).updateMatchSide(MATCH_ID, List.of(1L, 6L), "B", "확정");
        verify(assignmentMapper).markAssigned(eq(MATCH_ID), eq("MANUAL"), eq(MANAGER_ID), any(LocalDateTime.class),
                eq(4L), eq(1L), eq("매칭 완료"), eq("모집 중"));
    }

    static Stream<Arguments> invalidTeams() {
        return Stream.of(
                Arguments.of(List.of(1L, 1L), List.of(4L, 6L)),
                Arguments.of(List.of(1L, 2L), List.of(4L, 3L)),
                Arguments.of(List.of(1L, 2L, 4L), List.of(6L)),
                Arguments.of(List.of(1L, 2L), null));
    }

    @ParameterizedTest
    @MethodSource("invalidTeams")
    void rejectsManualTeamsThatDoNotMatchConfirmedParticipants(List<Long> teamA, List<Long> teamB) {
        givenLockedMatch(match("모집 중", TOMORROW));
        givenParticipants();

        AssignmentResult result = service.confirmManual(MATCH_ID, MANAGER_ID, teamA, teamB);

        assertThat(result.outcome()).isEqualTo(Outcome.INVALID_TEAMS);
        verify(assignmentMapper, never()).updateMatchSide(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void confirmsAutoAssignment() {
        givenLockedMatch(match("모집 중", TOMORROW));
        givenParticipants();
        when(assignmentMapper.updateMatchSide(eq(MATCH_ID), any(), anyString(), eq("확정"))).thenReturn(2);
        when(assignmentMapper.markAssigned(eq(MATCH_ID), anyString(), any(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(1);

        AssignmentResult result = service.confirmAuto(MATCH_ID, MANAGER_ID);

        assertThat(result.outcome()).isEqualTo(Outcome.ASSIGNED);
        verify(assignmentMapper).updateMatchSide(MATCH_ID, List.of(1L, 2L), "A", "확정");
        verify(assignmentMapper).updateMatchSide(MATCH_ID, List.of(4L, 6L), "B", "확정");
        verify(assignmentMapper).markAssigned(eq(MATCH_ID), eq("AUTO"), eq(MANAGER_ID), any(LocalDateTime.class),
                eq(1L), eq(4L), eq("매칭 완료"), eq("모집 중"));
    }

    @Test
    void rejectsConfirmWhenAlreadyAssigned() {
        givenLockedMatch(match("매칭 완료", TOMORROW));
        givenParticipants();

        AssignmentResult result = service.confirmAuto(MATCH_ID, MANAGER_ID);

        assertThat(result.outcome()).isEqualTo(Outcome.ALREADY_ASSIGNED);
        verify(assignmentMapper, never()).markAssigned(anyLong(), anyString(), any(), any(), any(), any(),
                anyString(), anyString());
    }

    @Test
    void resetsAssignmentToRecruiting() {
        givenLockedMatch(match("매칭 완료", TOMORROW));
        when(assignmentMapper.clearAssignment(MATCH_ID, "MANUAL", "모집 중", "매칭 완료")).thenReturn(1);

        AssignmentResult result = service.reset(MATCH_ID, MANAGER_ID);

        assertThat(result.outcome()).isEqualTo(Outcome.RESET);
        verify(assignmentMapper).clearMatchSides(MATCH_ID, "확정");
    }

    @Test
    void rejectsResetAfterMatchStarted() {
        givenLockedMatch(match("매칭 완료", LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1)));

        AssignmentResult result = service.reset(MATCH_ID, MANAGER_ID);

        assertThat(result.outcome()).isEqualTo(Outcome.STARTED);
        verify(assignmentMapper, never()).clearMatchSides(anyLong(), anyString());
        verify(assignmentMapper, never()).clearAssignment(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void deniesManagerOfAnotherFacilityForEveryOperation() {
        when(assignmentMapper.findAssignmentMatch(MATCH_ID)).thenReturn(Optional.of(match("모집 중", TOMORROW)));
        when(assignmentMapper.lockAssignmentMatch(MATCH_ID)).thenReturn(Optional.of(match("모집 중", TOMORROW)));
        when(assignmentMapper.isFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getView(MATCH_ID, MANAGER_ID)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.confirmAuto(MATCH_ID, MANAGER_ID)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.confirmManual(MATCH_ID, MANAGER_ID, List.of(1L, 2L), List.of(4L, 6L)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reset(MATCH_ID, MANAGER_ID)).isInstanceOf(AccessDeniedException.class);
        verify(assignmentMapper, never()).markAssigned(anyLong(), anyString(), any(), any(), any(), any(),
                anyString(), anyString());
        verify(assignmentMapper, never()).clearAssignment(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void returnsEmptyViewForMissingMatch() {
        when(assignmentMapper.findAssignmentMatch(MATCH_ID)).thenReturn(Optional.empty());

        assertThat(service.getView(MATCH_ID, MANAGER_ID)).isEmpty();
        verify(assignmentMapper, never()).isFacilityManager(anyLong(), anyLong());
    }

    private void givenLockedMatch(AssignmentMatch match) {
        when(assignmentMapper.lockAssignmentMatch(MATCH_ID)).thenReturn(Optional.of(match));
        when(assignmentMapper.isFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(true);
    }

    /** 확정 1·2·4·6, 대기 3. 자동 배정은 A=[1,2](2100), B=[4,6](2050)이 된다. */
    private void givenParticipants() {
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(List.of(
                participant(1L, "확정", "1200"),
                participant(2L, "확정", "900"),
                participant(3L, "대기", "1500"),
                participant(4L, "확정", "1100"),
                participant(6L, "확정", "950")));
    }

    private AssignmentMatch match(String status, LocalDateTime startAt) {
        var match = new AssignmentMatch();
        match.setMatchId(MATCH_ID);
        match.setFacilityId(FACILITY_ID);
        match.setStatus(status);
        match.setTeamACapacity(2);
        match.setTeamBCapacity(2);
        match.setStartAt(startAt);
        return match;
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
