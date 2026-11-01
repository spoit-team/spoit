package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import co.kr.spoit.facility.service.FacilityCapacityService;
import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.facility.service.FacilityScheduleUnavailableException;
import co.kr.spoit.match.dto.RoomCommand;
import co.kr.spoit.match.dto.RoomEditTarget;
import co.kr.spoit.match.dto.RoomForm;
import co.kr.spoit.match.mapper.RoomCommandMapper;

/** 매칭방 생성·수정·취소의 입력 검증, 잠금·검사 순서, 신청자 유무별 수정 범위를 검증한다. */
class RoomCommandServiceTest {

    private static final long MANAGER_ID = 7L;
    private static final long FACILITY_ID = 30L;
    private static final long SPORT_ID = 2L;
    private static final long MATCH_ID = 500L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    /** 기준 시각 2026-11-01 12:00 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);

    private RoomCommandMapper mapper;
    private FacilityReservationService reservationService;
    private FacilityCapacityService capacityService;
    private RoomCommandService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RoomCommandMapper.class);
        reservationService = mock(FacilityReservationService.class);
        capacityService = mock(FacilityCapacityService.class);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new RoomCommandService(mapper, reservationService, capacityService, clock);
    }

    /* ---------------- 생성 ---------------- */

    @Test
    @DisplayName("생성은 시설 잠금 → 운영 시간 → 동시 한도 검사 후 모집 중 상태로 저장한다")
    void createsRoomAfterChecksInOrder() {
        given(mapper.countManagedFacilitySport(MANAGER_ID, FACILITY_ID, SPORT_ID)).willReturn(1);
        givenInsertedId();

        long id = service.create(MANAGER_ID, form("2026-11-08", "10:00", "11:00"));

        assertThat(id).isEqualTo(MATCH_ID);
        LocalDateTime start = LocalDateTime.of(2026, 11, 8, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 8, 12, 0);
        InOrder order = inOrder(reservationService, capacityService, mapper);
        order.verify(reservationService).lockActiveFacility(FACILITY_ID);
        order.verify(reservationService).requireAvailable(FACILITY_ID, start, end);
        order.verify(capacityService).requireCapacity(FACILITY_ID, start, end);
        ArgumentCaptor<RoomCommand> captor = ArgumentCaptor.forClass(RoomCommand.class);
        order.verify(mapper).insertRoom(captor.capture());

        RoomCommand saved = captor.getValue();
        assertThat(saved.getCreatedBy()).isEqualTo(MANAGER_ID);
        assertThat(saved.getTitle()).isEqualTo("토요 풋살");
        assertThat(saved.getStatus()).isEqualTo("모집 중");
        // 팀당 정원 하나를 A팀·B팀에 똑같이 저장한다.
        assertThat(saved.getTeamACapacity()).isEqualTo(3);
        assertThat(saved.getTeamBCapacity()).isEqualTo(3);
        assertThat(saved.getMaxParticipants()).isEqualTo(6);
        // 최소 인원은 입력받지 않고 정원과 같은 값으로 저장한다.
        assertThat(saved.getMinParticipants()).isEqualTo(6);
        assertThat(saved.getRequiredSkill()).isEqualTo("중급");
    }

    @Test
    @DisplayName("23시 블록은 다음 날 0시에 끝난다")
    void lastHourEndsAtMidnight() {
        given(mapper.countManagedFacilitySport(MANAGER_ID, FACILITY_ID, SPORT_ID)).willReturn(1);
        givenInsertedId();

        service.create(MANAGER_ID, form("2026-11-08", "22:00", "23:00"));

        verify(reservationService).requireAvailable(FACILITY_ID,
                LocalDateTime.of(2026, 11, 8, 22, 0), LocalDateTime.of(2026, 11, 9, 0, 0));
    }

    @Test
    @DisplayName("연속되지 않은 시간, 지난 시간, 팀당 정원 초과, 알 수 없는 실력은 저장 전에 막는다")
    void rejectsInvalidInputBeforeTouchingFacility() {
        assertThatThrownBy(() -> service.create(MANAGER_ID, form("2026-11-08", "10:00", "12:00")))
                .isInstanceOf(RoomCommandException.class).hasMessageContaining("연속된");
        assertThatThrownBy(() -> service.create(MANAGER_ID, form("2026-11-01", "11:00")))
                .isInstanceOf(RoomCommandException.class).hasMessageContaining("지난 시간");

        // 자동 배정은 팀당 11명 이하일 때만 동작한다.
        RoomForm tooLargeTeam = form("2026-11-08", "10:00");
        tooLargeTeam.setTeamCapacity(12);
        assertThatThrownBy(() -> service.create(MANAGER_ID, tooLargeTeam))
                .isInstanceOf(RoomCommandException.class).hasMessageContaining("11명 이하");

        RoomForm unknownSkill = form("2026-11-08", "10:00");
        unknownSkill.setRequiredSkill("all");
        assertThatThrownBy(() -> service.create(MANAGER_ID, unknownSkill))
                .isInstanceOf(RoomCommandException.class).hasMessageContaining("실력 수준");

        RoomForm noHours = form("2026-11-08");
        assertThatThrownBy(() -> service.create(MANAGER_ID, noHours))
                .isInstanceOf(RoomCommandException.class).hasMessageContaining("하나 이상");

        verifyNoInteractions(reservationService, capacityService);
        verify(mapper, never()).insertRoom(any());
    }

    @Test
    @DisplayName("담당 시설의 제공 종목이 아니면 시설을 잠그지 않고 막는다")
    void rejectsUnmanagedFacilitySport() {
        given(mapper.countManagedFacilitySport(MANAGER_ID, FACILITY_ID, SPORT_ID)).willReturn(0);

        assertThatThrownBy(() -> service.create(MANAGER_ID, form("2026-11-08", "10:00")))
                .isInstanceOf(RoomCommandException.class).hasMessageContaining("담당 시설");

        verifyNoInteractions(reservationService, capacityService);
    }

    @Test
    @DisplayName("운영 시간·한도 검사 실패는 그대로 전달하고 저장하지 않는다")
    void propagatesScheduleFailure() {
        given(mapper.countManagedFacilitySport(MANAGER_ID, FACILITY_ID, SPORT_ID)).willReturn(1);
        willThrow(new FacilityScheduleUnavailableException("운영 시간이 아닙니다."))
                .given(reservationService).requireAvailable(eq(FACILITY_ID), any(), any());

        assertThatThrownBy(() -> service.create(MANAGER_ID, form("2026-11-08", "10:00")))
                .isInstanceOf(FacilityScheduleUnavailableException.class);

        verify(mapper, never()).insertRoom(any());
    }

    /* ---------------- 수정 ---------------- */

    @Test
    @DisplayName("신청자가 있으면 일정은 무시하고 제목·정원 등만 바꾼다")
    void updatesOnlyDetailsWhenReserved() {
        givenEditable("모집 중", LocalDateTime.of(2026, 11, 8, 10, 0));
        given(mapper.countReserved(eq(MATCH_ID), any(), anyString())).willReturn(3);

        RoomForm form = form(null);
        form.setFacilityId(null);
        form.setSportId(null);
        service.update(MANAGER_ID, MATCH_ID, form);

        ArgumentCaptor<RoomCommand> captor = ArgumentCaptor.forClass(RoomCommand.class);
        verify(mapper).updateRoomDetails(captor.capture());
        assertThat(captor.getValue().getMatchId()).isEqualTo(MATCH_ID);
        assertThat(captor.getValue().getStartAt()).isNull();
        verify(mapper, never()).updateRoom(any());
        verify(reservationService, never()).requireAvailable(anyLong(), any(), any());
    }

    @Test
    @DisplayName("신청자가 있으면 현재 인원보다 정원을 줄일 수 없다")
    void rejectsCapacityBelowReserved() {
        givenEditable("모집 중", LocalDateTime.of(2026, 11, 8, 10, 0));
        given(mapper.countReserved(eq(MATCH_ID), any(), anyString())).willReturn(7);

        assertThatThrownBy(() -> service.update(MANAGER_ID, MATCH_ID, form(null)))
                .isInstanceOf(RoomCommandException.class).hasMessageContaining("7명");

        verify(mapper, never()).updateRoomDetails(any());
    }

    @Test
    @DisplayName("신청자가 없으면 일정까지 바꾸고 동시 한도는 자기 자신을 빼고 검사한다")
    void updatesScheduleExcludingSelfWhenEmpty() {
        givenEditable("모집 중", LocalDateTime.of(2026, 11, 8, 10, 0));
        given(mapper.countReserved(eq(MATCH_ID), any(), anyString())).willReturn(0);
        given(mapper.countManagedFacilitySport(MANAGER_ID, FACILITY_ID, SPORT_ID)).willReturn(1);

        service.update(MANAGER_ID, MATCH_ID, form("2026-11-09", "18:00"));

        LocalDateTime start = LocalDateTime.of(2026, 11, 9, 18, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 9, 19, 0);
        // 시설 행은 대상 확인 때 한 번만 잠근다(같은 시설).
        verify(reservationService).lockActiveFacility(FACILITY_ID);
        verify(capacityService).requireCapacity(FACILITY_ID, start, end, MATCH_ID);
        ArgumentCaptor<RoomCommand> captor = ArgumentCaptor.forClass(RoomCommand.class);
        verify(mapper).updateRoom(captor.capture());
        assertThat(captor.getValue().getStartAt()).isEqualTo(start);
    }

    @Test
    @DisplayName("시설 잠금 후 방 행을 잠그며, 종료·취소·시작한 방은 수정할 수 없다")
    void rejectsNonEditableRooms() {
        givenEditable("종료", LocalDateTime.of(2026, 11, 8, 10, 0));
        assertThatThrownBy(() -> service.update(MANAGER_ID, MATCH_ID, form(null)))
                .isInstanceOf(RoomCommandException.class).hasMessageContaining("모집 중이거나");

        givenEditable("모집 중", NOW.minusMinutes(1));
        assertThatThrownBy(() -> service.cancel(MANAGER_ID, MATCH_ID))
                .isInstanceOf(RoomCommandException.class).hasMessageContaining("이미 시작한");

        InOrder order = inOrder(mapper, reservationService);
        order.verify(mapper).findRoom(MANAGER_ID, MATCH_ID);
        order.verify(reservationService).lockActiveFacility(FACILITY_ID);
        order.verify(mapper).findRoomForUpdate(MANAGER_ID, MATCH_ID);
        verify(mapper, never()).cancelRoom(anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("담당 밖·없는 방은 시설을 잠그지 않고 같은 문구로 막는다")
    void hidesForeignRooms() {
        given(mapper.findRoom(MANAGER_ID, MATCH_ID)).willReturn(null);

        assertThatThrownBy(() -> service.cancel(MANAGER_ID, MATCH_ID))
                .isInstanceOf(RoomCommandException.class).hasMessage("매칭방을 찾을 수 없습니다.");
        assertThatThrownBy(() -> service.update(MANAGER_ID, -1L, form(null)))
                .isInstanceOf(RoomCommandException.class).hasMessage("매칭방을 찾을 수 없습니다.");

        verifyNoInteractions(reservationService);
    }

    /* ---------------- 취소 ---------------- */

    @Test
    @DisplayName("취소는 방을 취소 상태로 바꾸고 활성 참가자·신청도 처리자와 함께 취소한다")
    void cancelsRoomAndActiveRows() {
        givenEditable("매칭 완료", LocalDateTime.of(2026, 11, 8, 10, 0));
        given(mapper.cancelRoom(eq(MATCH_ID), eq("취소"), any(), eq(NOW))).willReturn(1);

        service.cancel(MANAGER_ID, MATCH_ID);

        verify(mapper).cancelRoom(MATCH_ID, "취소", List.of("모집 중", "매칭 완료"), NOW);
        verify(mapper).cancelParticipants(MATCH_ID, "취소", List.of("대기", "확정"), NOW);
        verify(mapper).cancelApplications(MATCH_ID, "취소", List.of("대기", "확정"), MANAGER_ID, NOW);
    }

    @Test
    @DisplayName("방 상태가 이미 바뀌어 취소되지 않으면 신청은 건드리지 않는다")
    void doesNotTouchApplicationsWhenRoomNotCancelled() {
        givenEditable("모집 중", LocalDateTime.of(2026, 11, 8, 10, 0));
        given(mapper.cancelRoom(anyLong(), anyString(), any(), any())).willReturn(0);

        assertThatThrownBy(() -> service.cancel(MANAGER_ID, MATCH_ID))
                .isInstanceOf(RoomCommandException.class);

        verify(mapper, never()).cancelApplications(anyLong(), anyString(), any(), anyLong(), any());
        verify(mapper, never()).cancelParticipants(anyLong(), anyString(), any(), any());
    }

    /* ---------------- 픽스처 ---------------- */

    /** MyBatis useGeneratedKeys처럼 저장 후 생성 ID를 채운다. */
    private void givenInsertedId() {
        willAnswer(invocation -> {
            invocation.<RoomCommand>getArgument(0).setMatchId(MATCH_ID);
            return null;
        }).given(mapper).insertRoom(any());
    }

    private void givenEditable(String status, LocalDateTime startAt) {
        RoomEditTarget target = new RoomEditTarget();
        target.setMatchId(MATCH_ID);
        target.setFacilityId(FACILITY_ID);
        target.setSportId(SPORT_ID);
        target.setStatus(status);
        target.setStartAt(startAt);
        target.setEndAt(startAt.plusHours(2));
        given(mapper.findRoom(MANAGER_ID, MATCH_ID)).willReturn(target);
        given(mapper.findRoomForUpdate(MANAGER_ID, MATCH_ID)).willReturn(target);
    }

    private static RoomForm form(String date, String... hours) {
        RoomForm form = new RoomForm();
        form.setFacilityId(FACILITY_ID);
        form.setSportId(SPORT_ID);
        form.setTitle("  토요 풋살 ");
        form.setDate(date);
        form.setHours(List.of(hours));
        form.setTeamCapacity(3);
        form.setFeePerPerson(9000);
        form.setRequiredSkill("중급");
        return form;
    }
}
