package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.facility.service.FacilityScheduleUnavailableException;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;

/**
 * RES-03 개인 신청이 기존 매칭방이라도 시설의 최신 운영 일정을 다시 확인하는지 검증한다.
 *
 * <p>잠금 순서는 시설 → 매칭방이어야 하고, 시설 설정이 없거나 휴관·운영시간 밖이면
 * 신청 행을 만들지 않는다.</p>
 */
class MatchApplicationServiceApplyTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long USER_ID = 7L;
    private static final long MATCH_ID = 3L;
    private static final long FACILITY_ID = 11L;

    private MatchApplicationMapper applicationMapper;
    private MemberMapper memberMapper;
    private FacilityReservationService facilityReservationService;
    private MatchApplicationService service;

    private Member member;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(MatchApplicationMapper.class);
        memberMapper = mock(MemberMapper.class);
        facilityReservationService = mock(FacilityReservationService.class);
        service = new MatchApplicationService(applicationMapper, memberMapper,
                mock(SportRatingMapper.class), mock(MatchService.class), facilityReservationService);

        member = new Member();
        member.setUserId(USER_ID);
        given(memberMapper.findById(USER_ID)).willReturn(Optional.of(member));
        given(applicationMapper.findFacilityId(MATCH_ID)).willReturn(FACILITY_ID);
    }

    @Test
    @DisplayName("시설을 먼저 잠그고 매칭방을 잠근 뒤 운영 일정을 검증한다")
    void locksFacilityBeforeMatchAndChecksSchedule() {
        LocalDateTime startAt = LocalDateTime.now(KST).plusDays(1);
        LocalDateTime endAt = startAt.plusHours(2);
        given(applicationMapper.lockMatch(MATCH_ID)).willReturn(recruitingMatch(startAt, endAt));
        given(applicationMapper.existsActiveEntry(anyLong(), anyLong(), anyList())).willReturn(false);
        given(applicationMapper.countOccupiedSeats(anyLong(), anyList(), anyString(), anyString())).willReturn(0);
        given(applicationMapper.insert(any(MatchApplication.class))).willReturn(1);

        String receiptNo = service.applyIndividual(MATCH_ID, USER_ID);

        assertThat(receiptNo).startsWith("RCPT-");
        InOrder order = inOrder(facilityReservationService, applicationMapper);
        order.verify(facilityReservationService).lockActiveFacility(FACILITY_ID);
        order.verify(applicationMapper).lockMatch(MATCH_ID);
        order.verify(facilityReservationService).requireAvailable(FACILITY_ID, startAt, endAt);
        order.verify(applicationMapper).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("매칭방이 없으면 시설을 잠그지 않고 신청도 만들지 않는다")
    void rejectsMissingMatch() {
        given(applicationMapper.findFacilityId(MATCH_ID)).willReturn(null);

        assertThatThrownBy(() -> service.applyIndividual(MATCH_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("매칭방 정보를 찾을 수 없습니다.");
        verify(facilityReservationService, never()).lockActiveFacility(anyLong());
        verify(applicationMapper, never()).lockMatch(anyLong());
        verify(applicationMapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("시설이 삭제·비활성이라 잠글 수 없으면 사용자 안내로 실패하고 신청은 만들지 않는다")
    void rejectsUnavailableFacilityAtLock() {
        FacilityScheduleUnavailableException unavailable = unavailable("현재 운영 중인 시설이 아닙니다.");
        willThrow(unavailable).given(facilityReservationService).lockActiveFacility(FACILITY_ID);

        assertThatThrownBy(() -> service.applyIndividual(MATCH_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("현재 운영 중인 시설이 아닙니다.");
        verify(applicationMapper, never()).lockMatch(anyLong());
        verify(applicationMapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("운영시간이 설정되지 않았거나 휴관이면 신청을 막고 신청 행을 만들지 않는다")
    void rejectsUnconfiguredOrClosedFacility() {
        LocalDateTime startAt = LocalDateTime.now(KST).plusDays(1);
        given(applicationMapper.lockMatch(MATCH_ID)).willReturn(recruitingMatch(startAt, startAt.plusHours(2)));
        FacilityScheduleUnavailableException unavailable = unavailable("해당 시간에는 시설을 운영하지 않습니다.");
        willThrow(unavailable).given(facilityReservationService)
                .requireAvailable(FACILITY_ID, startAt, startAt.plusHours(2));

        assertThatThrownBy(() -> service.applyIndividual(MATCH_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("해당 시간에는 시설을 운영하지 않습니다.");
        verify(applicationMapper, never()).existsActiveEntry(anyLong(), anyLong(), anyList());
        verify(applicationMapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("시설 안내 문구가 비어 있으면 기본 안내로 대체한다")
    void fallsBackToDefaultMessage() {
        LocalDateTime startAt = LocalDateTime.now(KST).plusDays(1);
        given(applicationMapper.lockMatch(MATCH_ID)).willReturn(recruitingMatch(startAt, startAt.plusHours(2)));
        willThrow(unavailable(" ")).given(facilityReservationService)
                .requireAvailable(FACILITY_ID, startAt, startAt.plusHours(2));

        assertThatThrownBy(() -> service.applyIndividual(MATCH_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("현재 시설 운영 일정으로는 신청할 수 없습니다.");
    }

    @Test
    @DisplayName("후보 시설과 잠근 매칭방의 시설이 다르면 운영 일정을 검증하지 않고 실패한다")
    void rejectsStaleFacilityId() {
        LocalDateTime startAt = LocalDateTime.now(KST).plusDays(1);
        MatchRow match = recruitingMatch(startAt, startAt.plusHours(2));
        match.setFacilityId(FACILITY_ID + 1);
        given(applicationMapper.lockMatch(MATCH_ID)).willReturn(match);

        assertThatThrownBy(() -> service.applyIndividual(MATCH_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("매칭방 정보가 변경되었습니다. 다시 시도해 주세요.");
        verify(facilityReservationService, never()).requireAvailable(anyLong(), any(), any());
        verify(applicationMapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("매칭방 시간이 비어 있으면 시설 운영 일정을 검증하지 않고 실패한다")
    void rejectsMissingMatchTime() {
        MatchRow match = recruitingMatch(LocalDateTime.now(KST).plusDays(1), null);
        given(applicationMapper.lockMatch(MATCH_ID)).willReturn(match);

        assertThatThrownBy(() -> service.applyIndividual(MATCH_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("매칭방 이용 시간을 확인할 수 없습니다.");
        verify(facilityReservationService, never()).requireAvailable(anyLong(), any(), any());
        verify(applicationMapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("시설 일정이 유효해도 모집 중이 아니면 기존 안내로 막는다")
    void keepsExistingStatusCheck() {
        LocalDateTime startAt = LocalDateTime.now(KST).plusDays(1);
        MatchRow match = recruitingMatch(startAt, startAt.plusHours(2));
        match.setStatus("매칭 완료");
        given(applicationMapper.lockMatch(MATCH_ID)).willReturn(match);

        assertThatThrownBy(() -> service.applyIndividual(MATCH_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("모집 중인 매칭방이 아닙니다.");
        verify(applicationMapper, never()).insert(any(MatchApplication.class));
    }

    private static MatchRow recruitingMatch(LocalDateTime startAt, LocalDateTime endAt) {
        MatchRow m = new MatchRow();
        m.setMatchId(MATCH_ID);
        m.setFacilityId(FACILITY_ID);
        m.setStatus("모집 중");
        m.setStartAt(startAt);
        m.setEndAt(endAt);
        m.setMaxParticipants(10);
        return m;
    }

    /**
     * 시설 백엔드가 동시에 작성 중이라 예외 생성자 시그니처에 의존하지 않도록
     * 메시지만 스텁한다.
     */
    private static FacilityScheduleUnavailableException unavailable(String message) {
        FacilityScheduleUnavailableException e = mock(FacilityScheduleUnavailableException.class);
        given(e.getMessage()).willReturn(message);
        return e;
    }
}
