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
import org.springframework.mock.web.MockMultipartFile;

import co.kr.spoit.facility.service.FacilityCapacityService;
import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.facility.service.FacilityScheduleUnavailableException;
import co.kr.spoit.global.upload.PhotoStorage;
import co.kr.spoit.global.upload.UploadedPhoto;
import co.kr.spoit.match.dto.RoomCommand;
import co.kr.spoit.match.dto.RoomEditTarget;
import co.kr.spoit.match.dto.RoomForm;
import co.kr.spoit.match.mapper.RoomCommandMapper;
import co.kr.spoit.notification.service.NotificationDispatcher;
import co.kr.spoit.payment.service.PaymentLedgerService;

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
    private PhotoStorage photoStorage;
    private NotificationDispatcher notificationDispatcher;
    private PaymentLedgerService paymentLedger;
    private RoomCommandService service;

    /** JPG 시그니처로 시작하는 최소 바이트 */
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

    @BeforeEach
    void setUp() {
        mapper = mock(RoomCommandMapper.class);
        reservationService = mock(FacilityReservationService.class);
        capacityService = mock(FacilityCapacityService.class);
        photoStorage = mock(PhotoStorage.class);
        notificationDispatcher = mock(NotificationDispatcher.class);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        paymentLedger = mock(PaymentLedgerService.class);
        service = new RoomCommandService(mapper, reservationService, capacityService, photoStorage,
                notificationDispatcher, paymentLedger, clock);
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
        given(mapper.findCancelRecipientUserIds(eq(MATCH_ID), any(), any()))
                .willReturn(List.of(21L, 21L, 22L));

        service.cancel(MANAGER_ID, MATCH_ID);

        verify(mapper).cancelRoom(MATCH_ID, "취소", List.of("모집 중", "매칭 완료"), NOW);
        verify(mapper).cancelParticipants(MATCH_ID, "취소", List.of("대기", "확정"), NOW);
        verify(mapper).cancelApplications(MATCH_ID, "취소", List.of("대기", "확정"), MANAGER_ID, NOW);
        // 상태 변경 전에 모은 수신자를 중복 없이 한 번만 넘긴다.
        verify(notificationDispatcher).notifyAfterCommit(List.of(21L, 22L),
                "MATCH_ROOM_CANCELLED", "매칭방이 취소되었습니다",
                "참가 신청한 매칭방이 시설 사정으로 취소되었습니다. 매칭 화면에서 확인해 주세요.",
                MATCH_ID, null, null);
    }

    @Test
    @DisplayName("수신자를 읽고 FK 검사에 필요한 계정을 잠근 뒤 방 상태를 바꾼다")
    void locksCancellationAccountsBeforeStatusChange() {
        givenEditable("모집 중", LocalDateTime.of(2026, 11, 8, 10, 0));
        given(mapper.cancelRoom(eq(MATCH_ID), eq("취소"), any(), eq(NOW))).willReturn(1);
        given(mapper.findCancelRecipientUserIds(eq(MATCH_ID), any(), any())).willReturn(List.of(21L));

        service.cancel(MANAGER_ID, MATCH_ID);

        InOrder order = inOrder(mapper);
        order.verify(mapper).findCancelRecipientUserIds(eq(MATCH_ID), any(), any());
        order.verify(mapper).lockCancellationAccounts(List.of(21L, MANAGER_ID));
        order.verify(mapper).cancelRoom(eq(MATCH_ID), eq("취소"), any(), eq(NOW));
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

    /* ---------------- 사진 ---------------- */

    @Test
    @DisplayName("생성 때 첨부한 사진을 저장하고 URL을 함께 저장한다")
    void createsRoomWithPhoto() {
        given(mapper.countManagedFacilitySport(MANAGER_ID, FACILITY_ID, SPORT_ID)).willReturn(1);
        givenInsertedId();
        given(photoStorage.store(eq("matches"), any(UploadedPhoto.class))).willReturn("/uploads/matches/a.jpg");

        RoomForm form = form("2026-11-08", "10:00");
        form.setPhoto(new MockMultipartFile("photo", "court.jpg", "image/jpeg", JPG));
        service.create(MANAGER_ID, form);

        ArgumentCaptor<RoomCommand> captor = ArgumentCaptor.forClass(RoomCommand.class);
        verify(mapper).insertRoom(captor.capture());
        assertThat(captor.getValue().getPhotoUrl()).isEqualTo("/uploads/matches/a.jpg");
    }

    @Test
    @DisplayName("사진 없이 만들면 저장소를 쓰지 않고 URL은 비운다")
    void createsRoomWithoutPhoto() {
        given(mapper.countManagedFacilitySport(MANAGER_ID, FACILITY_ID, SPORT_ID)).willReturn(1);
        givenInsertedId();

        service.create(MANAGER_ID, form("2026-11-08", "10:00"));

        ArgumentCaptor<RoomCommand> captor = ArgumentCaptor.forClass(RoomCommand.class);
        verify(mapper).insertRoom(captor.capture());
        assertThat(captor.getValue().getPhotoUrl()).isNull();
        verifyNoInteractions(photoStorage);
    }

    @Test
    @DisplayName("이미지가 아니거나 5MB를 넘는 사진은 시설을 건드리기 전에 막는다")
    void rejectsInvalidPhoto() {
        RoomForm notImage = form("2026-11-08", "10:00");
        notImage.setPhoto(new MockMultipartFile("photo", "court.jpg", "image/jpeg", "hello".getBytes()));
        assertThatThrownBy(() -> service.create(MANAGER_ID, notImage))
                .isInstanceOf(RoomCommandException.class)
                .hasMessageContaining("JPG·PNG·WEBP");

        byte[] big = new byte[(int) UploadedPhoto.MAX_BYTES + 1];
        System.arraycopy(JPG, 0, big, 0, JPG.length);
        RoomForm tooBig = form("2026-11-08", "10:00");
        tooBig.setPhoto(new MockMultipartFile("photo", "big.jpg", "image/jpeg", big));
        assertThatThrownBy(() -> service.create(MANAGER_ID, tooBig))
                .isInstanceOf(RoomCommandException.class)
                .hasMessageContaining("5MB");

        verifyNoInteractions(reservationService, photoStorage);
        verify(mapper, never()).insertRoom(any());
    }

    @Test
    @DisplayName("신청자가 있어도 사진은 교체할 수 있고, 이전 파일은 지운다")
    void replacesPhotoWhenReserved() {
        givenEditable("모집 중", LocalDateTime.of(2026, 11, 8, 10, 0));
        mapper.findRoomForUpdate(MANAGER_ID, MATCH_ID).setPhotoUrl("/uploads/matches/old.jpg");
        given(mapper.countReserved(eq(MATCH_ID), any(), anyString())).willReturn(3);
        given(photoStorage.store(eq("matches"), any(UploadedPhoto.class))).willReturn("/uploads/matches/new.jpg");

        RoomForm form = form(null);
        form.setPhoto(new MockMultipartFile("photo", "court.jpg", "image/jpeg", JPG));
        service.update(MANAGER_ID, MATCH_ID, form);

        ArgumentCaptor<RoomCommand> captor = ArgumentCaptor.forClass(RoomCommand.class);
        verify(mapper).updateRoomDetails(captor.capture());
        assertThat(captor.getValue().isPhotoChanged()).isTrue();
        assertThat(captor.getValue().getPhotoUrl()).isEqualTo("/uploads/matches/new.jpg");
        verify(photoStorage).delete("/uploads/matches/old.jpg");
    }

    @Test
    @DisplayName("사진을 바꾸지 않으면 기존 URL을 그대로 두고, 삭제를 고르면 비운다")
    void keepsOrRemovesPhoto() {
        given(mapper.countManagedFacilitySport(MANAGER_ID, FACILITY_ID, SPORT_ID)).willReturn(1);
        givenEditable("모집 중", LocalDateTime.of(2026, 11, 8, 10, 0));
        mapper.findRoomForUpdate(MANAGER_ID, MATCH_ID).setPhotoUrl("/uploads/matches/old.jpg");
        given(mapper.countReserved(eq(MATCH_ID), any(), anyString())).willReturn(0);

        service.update(MANAGER_ID, MATCH_ID, form("2026-11-08", "10:00"));
        ArgumentCaptor<RoomCommand> kept = ArgumentCaptor.forClass(RoomCommand.class);
        verify(mapper).updateRoom(kept.capture());
        assertThat(kept.getValue().isPhotoChanged()).isFalse();
        verify(photoStorage, never()).delete(anyString());

        RoomForm remove = form("2026-11-08", "10:00");
        remove.setRemovePhoto(true);
        service.update(MANAGER_ID, MATCH_ID, remove);
        ArgumentCaptor<RoomCommand> removed = ArgumentCaptor.forClass(RoomCommand.class);
        verify(mapper, org.mockito.Mockito.times(2)).updateRoom(removed.capture());
        assertThat(removed.getValue().isPhotoChanged()).isTrue();
        assertThat(removed.getValue().getPhotoUrl()).isNull();
        verify(photoStorage).delete("/uploads/matches/old.jpg");
    }

    @Test
    @DisplayName("사진 저장소 오류는 안내 문구로 바꾸고 방을 만들지 않는다")
    void reportsStorageFailure() {
        given(mapper.countManagedFacilitySport(MANAGER_ID, FACILITY_ID, SPORT_ID)).willReturn(1);
        willThrow(new IllegalStateException("S3 down")).given(photoStorage).store(anyString(), any());

        RoomForm form = form("2026-11-08", "10:00");
        form.setPhoto(new MockMultipartFile("photo", "court.jpg", "image/jpeg", JPG));
        assertThatThrownBy(() -> service.create(MANAGER_ID, form))
                .isInstanceOf(RoomCommandException.class)
                .hasMessageContaining("사진을 저장하지 못했습니다");
        verify(mapper, never()).insertRoom(any());
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

    /* ---------------- 참가비 결제 ---------------- */

    @Test
    @DisplayName("신청자가 있으면 결제한 금액과 어긋나지 않도록 참가비를 바꿀 수 없다")
    void rejectsFeeChangeWhenReserved() {
        givenEditable("모집 중", LocalDateTime.of(2026, 11, 8, 10, 0));
        mapper.findRoomForUpdate(MANAGER_ID, MATCH_ID).setFeePerPerson(new java.math.BigDecimal("5000.00"));
        given(mapper.countReserved(eq(MATCH_ID), any(), anyString())).willReturn(3);

        assertThatThrownBy(() -> service.update(MANAGER_ID, MATCH_ID, form(null)))
                .isInstanceOf(RoomCommandException.class)
                .hasMessage("신청자가 있는 매칭방은 참가비를 바꿀 수 없습니다.");
        verify(mapper, never()).updateRoomDetails(any());
    }

    @Test
    @DisplayName("신청자가 있어도 참가비가 그대로면 다른 값은 수정할 수 있다")
    void keepsSameFeeWhenReserved() {
        givenEditable("모집 중", LocalDateTime.of(2026, 11, 8, 10, 0));
        mapper.findRoomForUpdate(MANAGER_ID, MATCH_ID).setFeePerPerson(new java.math.BigDecimal("9000.00"));
        given(mapper.countReserved(eq(MATCH_ID), any(), anyString())).willReturn(3);

        service.update(MANAGER_ID, MATCH_ID, form(null));

        verify(mapper).updateRoomDetails(any());
    }

    @Test
    @DisplayName("매칭방 취소는 신청 상태를 바꾸기 전에 활성 결제 신청을 전액 환불 요청한다")
    void cancelRefundsBeforeStatusChange() {
        givenEditable("모집 중", LocalDateTime.of(2026, 11, 8, 10, 0));
        given(mapper.cancelRoom(eq(MATCH_ID), eq("취소"), any(), eq(NOW))).willReturn(1);
        given(mapper.findCancelRecipientUserIds(eq(MATCH_ID), any(), any())).willReturn(List.of());

        service.cancel(MANAGER_ID, MATCH_ID);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(paymentLedger, mapper);
        order.verify(paymentLedger).refundActiveApplicationsOfMatch(MATCH_ID, "매칭방 취소", NOW);
        order.verify(mapper).cancelApplications(MATCH_ID, "취소", List.of("대기", "확정"), MANAGER_ID, NOW);
    }
}
