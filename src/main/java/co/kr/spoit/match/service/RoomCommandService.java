package co.kr.spoit.match.service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.kr.spoit.facility.service.FacilityCapacityService;
import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.global.upload.InvalidPhotoException;
import co.kr.spoit.global.upload.PhotoStorage;
import co.kr.spoit.global.upload.UploadedPhoto;
import co.kr.spoit.mat.service.AutoTeamAssigner;
import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.match.domain.SkillLevel;
import co.kr.spoit.match.dto.RoomCommand;
import co.kr.spoit.match.dto.RoomEditTarget;
import co.kr.spoit.match.dto.RoomForm;
import co.kr.spoit.match.mapper.RoomCommandMapper;
import co.kr.spoit.notification.service.NotificationDispatcher;

/**
 * 시설 관리자의 담당 시설 매칭방 생성·수정·취소를 담당한다.
 *
 * <p>잠금 순서는 항상 시설 행 → 매칭방 행이다. 시설 일정 변경도 시설 행을 먼저 잠그므로 같은 순서를
 * 지켜 교착을 피한다. 운영 시간·휴관은 {@link FacilityReservationService}, 동시 대관 한도는
 * {@link FacilityCapacityService}가 최신 값으로 다시 검사한다.</p>
 *
 * <p>신청자(참가자 행 또는 대기 신청)가 있는 방은 시설·종목·일정을 바꿀 수 없고, 정원은 현재 인원
 * 아래로 줄일 수 없다. 삭제는 취소 처리로 대신하며 활성 신청·참가자도 함께 취소한다.</p>
 *
 * <p>시설 사진은 신청자 유무와 관계없이 바꿀 수 있다. 파일은 DB 반영 전에 {@link PhotoStorage}에 올리고,
 * 트랜잭션이 롤백되면 새 파일을, 커밋되면 교체·삭제된 이전 파일을 지운다.</p>
 */
@Service
@Transactional
public class RoomCommandService {

    static final int TITLE_MAX_LENGTH = 70;
    /** 팀당 정원 상한. 경기 시작 자동 배정이 지원하는 범위와 같아야 한다. */
    static final int TEAM_CAPACITY_MAX = AutoTeamAssigner.MAX_TEAM_CAPACITY;
    static final int FEE_MAX = 1_000_000;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    /** 매칭방 사진 저장 폴더 */
    static final String PHOTO_DIRECTORY = "matches";

    private static final Logger log = LoggerFactory.getLogger(RoomCommandService.class);

    private static final int MYSQL_LOCK_NOWAIT = 3572;

    /** 수정·취소할 수 있는 방 상태 */
    static final List<String> EDITABLE_STATUSES = List.of(
            MatchStatus.RECRUITING.dbValue(), MatchStatus.MATCHED.dbValue());

    private final RoomCommandMapper roomCommandMapper;
    private final FacilityReservationService reservationService;
    private final FacilityCapacityService capacityService;
    private final PhotoStorage photoStorage;
    private final NotificationDispatcher notificationDispatcher;
    private final Clock clock;

    @Autowired
    public RoomCommandService(RoomCommandMapper roomCommandMapper,
            FacilityReservationService reservationService, FacilityCapacityService capacityService,
            PhotoStorage photoStorage, NotificationDispatcher notificationDispatcher) {
        this(roomCommandMapper, reservationService, capacityService, photoStorage, notificationDispatcher,
                Clock.system(ZONE));
    }

    RoomCommandService(RoomCommandMapper roomCommandMapper, FacilityReservationService reservationService,
            FacilityCapacityService capacityService, PhotoStorage photoStorage,
            NotificationDispatcher notificationDispatcher, Clock clock) {
        this.roomCommandMapper = roomCommandMapper;
        this.reservationService = reservationService;
        this.capacityService = capacityService;
        this.photoStorage = photoStorage;
        this.notificationDispatcher = notificationDispatcher;
        this.clock = clock;
    }

    /** 담당 시설에 매칭방을 만들고 생성 ID를 돌려준다. */
    public long create(Long managerUserId, RoomForm form) {
        requireManager(managerUserId);
        Details details = parseDetails(form);
        Schedule schedule = parseSchedule(form);
        UploadedPhoto photo = parsePhoto(form);
        requireManagedFacilitySport(managerUserId, schedule);

        reservationService.lockActiveFacility(schedule.facilityId());
        reservationService.requireAvailable(schedule.facilityId(), schedule.startAt(), schedule.endAt());
        capacityService.requireCapacity(schedule.facilityId(), schedule.startAt(), schedule.endAt());

        PhotoChange photoChange = photo == null ? PhotoChange.UNCHANGED : PhotoChange.to(storePhoto(photo));
        RoomCommand command = command(null, managerUserId, details, schedule, MatchStatus.RECRUITING.dbValue(),
                photoChange);
        roomCommandMapper.insertRoom(command);
        if (command.getMatchId() == null) {
            throw new IllegalStateException("매칭방 생성 ID를 받지 못했습니다.");
        }
        return command.getMatchId();
    }

    /**
     * 담당 시설 매칭방을 수정한다. 신청자가 있으면 제목·정원·참가비·실력 수준·사진만 반영하고
     * 폼의 시설·종목·일정 값은 무시한다.
     */
    public void update(Long managerUserId, Long matchId, RoomForm form) {
        requireManager(managerUserId);
        RoomEditTarget target = lockEditableRoom(managerUserId, matchId);
        Details details = parseDetails(form);
        UploadedPhoto photo = parsePhoto(form);
        int reserved = countReserved(target.getMatchId());

        if (reserved > 0) {
            if (details.maxParticipants() < reserved) {
                throw new RoomCommandException(
                        "현재 참가·신청 인원(" + reserved + "명)보다 정원을 줄일 수 없습니다.");
            }
            PhotoChange photoChange = photoChange(target, form, photo);
            roomCommandMapper.updateRoomDetails(
                    command(target.getMatchId(), null, details, null, null, photoChange));
            return;
        }

        Schedule schedule = parseSchedule(form);
        requireManagedFacilitySport(managerUserId, schedule);
        if (!schedule.facilityId().equals(target.getFacilityId())) {
            reservationService.lockActiveFacility(schedule.facilityId());
        }
        reservationService.requireAvailable(schedule.facilityId(), schedule.startAt(), schedule.endAt());
        capacityService.requireCapacity(schedule.facilityId(), schedule.startAt(), schedule.endAt(),
                target.getMatchId());
        PhotoChange photoChange = photoChange(target, form, photo);
        roomCommandMapper.updateRoom(command(target.getMatchId(), null, details, schedule, null, photoChange));
    }

    /** 시설·매칭방을 잠근 뒤 그 시점의 활성 신청·참가자를 취소하고 알림을 저장한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(Long managerUserId, Long matchId) {
        requireManager(managerUserId);
        RoomEditTarget target = lockEditableRoom(managerUserId, matchId);
        LocalDateTime now = now();

        // 잠금을 기다리는 동안 커밋된 신청도 포함해, 상태를 바꾸기 전에 수신자를 확정한다.
        List<Long> recipients = distinctUserIds(
                roomCommandMapper.findCancelRecipientUserIds(target.getMatchId(),
                        ParticipationStatus.occupyingValues(), ApplicationStatus.activeValues()));
        lockCancellationAccounts(managerUserId, recipients);

        int updated = roomCommandMapper.cancelRoom(target.getMatchId(), MatchStatus.CANCELLED.dbValue(),
                EDITABLE_STATUSES, now);
        if (updated != 1) {
            throw new RoomCommandException("매칭방을 취소하지 못했습니다. 다시 시도해 주세요.");
        }
        roomCommandMapper.cancelParticipants(target.getMatchId(), ParticipationStatus.CANCELLED.dbValue(),
                ParticipationStatus.occupyingValues(), now);
        roomCommandMapper.cancelApplications(target.getMatchId(), ApplicationStatus.CANCELLED.dbValue(),
                ApplicationStatus.activeValues(), managerUserId, now);

        notificationDispatcher.notifyAfterCommit(recipients,
                "MATCH_ROOM_CANCELLED", "매칭방이 취소되었습니다",
                "참가 신청한 매칭방이 시설 사정으로 취소되었습니다. 매칭 화면에서 확인해 주세요.",
                target.getMatchId(), null, null);
    }

    private void lockCancellationAccounts(Long managerUserId, List<Long> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        // 신청은 계정을 먼저 잠근다. 시설 락을 가진 채 FK 검사에서 계정 락을 기다리지 않는다.
        List<Long> accountIds = new ArrayList<>(recipients);
        accountIds.add(managerUserId);
        try {
            roomCommandMapper.lockCancellationAccounts(accountIds);
        } catch (DataAccessException exception) {
            if (exception.getMostSpecificCause() instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MYSQL_LOCK_NOWAIT) {
                throw new RoomCommandException("관련 회원 정보가 처리 중입니다. 잠시 후 매칭방 취소를 다시 시도해 주세요.");
            }
            throw exception;
        }
    }

    /** 순서를 유지하며 null·중복을 제거한다. */
    private static List<Long> distinctUserIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return List.copyOf(ids.stream().filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /* ---------------- 대상 확인 ---------------- */

    /**
     * 시설 행을 먼저 잠근 뒤 매칭방 행을 잠그고, 수정·취소 가능한 상태인지 확인한다.
     * 담당 밖·삭제된 시설·없는 방은 모두 같은 문구로 막아 방 존재 여부를 드러내지 않는다.
     */
    private RoomEditTarget lockEditableRoom(Long managerUserId, Long matchId) {
        if (matchId == null || matchId <= 0) {
            throw notFound();
        }
        RoomEditTarget snapshot = roomCommandMapper.findRoom(managerUserId, matchId);
        if (snapshot == null) {
            throw notFound();
        }
        reservationService.lockActiveFacility(snapshot.getFacilityId());
        RoomEditTarget target = roomCommandMapper.findRoomForUpdate(managerUserId, matchId);
        if (target == null) {
            throw notFound();
        }
        if (!EDITABLE_STATUSES.contains(target.getStatus())) {
            throw new RoomCommandException("모집 중이거나 매칭 완료된 매칭방만 수정·취소할 수 있습니다.");
        }
        if (target.getStartAt() == null || !target.getStartAt().isAfter(now())) {
            throw new RoomCommandException("이미 시작한 매칭방은 수정·취소할 수 없습니다.");
        }
        return target;
    }

    private int countReserved(Long matchId) {
        return roomCommandMapper.countReserved(matchId, ParticipationStatus.occupyingValues(),
                ApplicationStatus.WAITING.dbValue());
    }

    private void requireManagedFacilitySport(Long managerUserId, Schedule schedule) {
        if (roomCommandMapper.countManagedFacilitySport(managerUserId, schedule.facilityId(),
                schedule.sportId()) < 1) {
            throw new RoomCommandException("담당 시설에서 제공하는 종목을 선택해 주세요.");
        }
    }

    private static void requireManager(Long managerUserId) {
        if (managerUserId == null || managerUserId <= 0) {
            throw new RoomCommandException("로그인이 필요합니다.");
        }
    }

    private static RoomCommandException notFound() {
        return new RoomCommandException("매칭방을 찾을 수 없습니다.");
    }

    /* ---------------- 입력 검증 ---------------- */

    /** 신청자 유무와 관계없이 항상 수정할 수 있는 값 */
    /**
     * {@code teamCapacity}는 팀당 정원이다. 경기 시작 자동 배정은 두 팀 정원이 같을 때만 동작하므로
     * A팀·B팀 정원에 같은 값을 저장한다.
     */
    private record Details(String title, int teamCapacity, int fee, String requiredSkill) {

        int maxParticipants() {
            return teamCapacity * 2;
        }

        /**
         * 최소 인원은 정책에서 빠졌지만 DB 컬럼은 NOT NULL이고 정원 이하여야 한다.
         * 경기는 정원이 차야 진행되므로 정원과 같은 값을 저장한다.
         */
        int minParticipants() {
            return maxParticipants();
        }
    }

    /** 신청자가 없을 때만 바꿀 수 있는 시설·종목·일정 */
    private record Schedule(Long facilityId, Long sportId, LocalDateTime startAt, LocalDateTime endAt) {
    }

    private static Details parseDetails(RoomForm form) {
        if (form == null) {
            throw new RoomCommandException("입력 정보를 확인해 주세요.");
        }
        String title = form.getTitle() == null ? "" : form.getTitle().strip();
        if (title.isEmpty()) {
            throw new RoomCommandException("방 이름을 입력해 주세요.");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new RoomCommandException("방 이름은 " + TITLE_MAX_LENGTH + "자 이하로 입력해 주세요.");
        }
        int team = requireTeamCapacity(form.getTeamCapacity());
        Integer fee = form.getFeePerPerson();
        if (fee == null || fee < 0 || fee > FEE_MAX) {
            throw new RoomCommandException("참가비는 0원 이상 " + FEE_MAX + "원 이하로 입력해 주세요.");
        }
        SkillLevel skill = SkillLevel.fromDbValue(form.getRequiredSkill());
        if (skill == null) {
            throw new RoomCommandException("실력 수준을 선택해 주세요.");
        }
        return new Details(title, team, fee, skill.dbValue());
    }

    private static int requireTeamCapacity(Integer value) {
        if (value == null || value < 1 || value > TEAM_CAPACITY_MAX) {
            throw new RoomCommandException("팀당 정원은 1명 이상 " + TEAM_CAPACITY_MAX + "명 이하로 입력해 주세요.");
        }
        return value;
    }

    /** 날짜와 연속된 1시간 블록으로 [시작, 종료) 구간을 만든다. 시작은 지금 이후여야 한다. */
    private Schedule parseSchedule(RoomForm form) {
        if (form.getFacilityId() == null || form.getFacilityId() <= 0) {
            throw new RoomCommandException("담당 시설을 선택해 주세요.");
        }
        if (form.getSportId() == null || form.getSportId() <= 0) {
            throw new RoomCommandException("종목을 선택해 주세요.");
        }
        LocalDate date = parseDate(form.getDate());
        TreeSet<Integer> hours = parseHours(form.getHours());

        int first = hours.first();
        int last = hours.last();
        if (last - first + 1 != hours.size()) {
            throw new RoomCommandException("예약 시간은 연속된 블록만 선택할 수 있습니다.");
        }
        LocalDateTime startAt = date.atTime(first, 0);
        // 23시 블록은 다음 날 00시에 끝난다.
        LocalDateTime endAt = date.atTime(last, 0).plusHours(1);
        if (!startAt.isAfter(now())) {
            throw new RoomCommandException("지난 시간에는 매칭방을 만들 수 없습니다.");
        }
        return new Schedule(form.getFacilityId(), form.getSportId(), startAt, endAt);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RoomCommandException("이용 날짜를 선택해 주세요.");
        }
        try {
            return LocalDate.parse(raw.strip());
        } catch (DateTimeParseException exception) {
            throw new RoomCommandException("이용 날짜를 확인해 주세요.");
        }
    }

    /** "HH:00" 형식만 받는다. 중복은 하나로 합친다. */
    private static TreeSet<Integer> parseHours(List<String> raw) {
        TreeSet<Integer> hours = new TreeSet<>();
        if (raw != null) {
            for (String value : raw) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                try {
                    LocalTime time = LocalTime.parse(value.strip());
                    if (time.getMinute() != 0 || time.getSecond() != 0) {
                        throw new RoomCommandException("예약 시간은 1시간 단위로 선택해 주세요.");
                    }
                    hours.add(time.getHour());
                } catch (DateTimeParseException exception) {
                    throw new RoomCommandException("예약 시간을 확인해 주세요.");
                }
            }
        }
        if (hours.isEmpty()) {
            throw new RoomCommandException("예약 시간 블록을 하나 이상 선택해 주세요.");
        }
        return hours;
    }

    /* ---------------- 사진 ---------------- */

    /** 사진 URL 변경 내용. {@code changed}가 false이면 DB 값을 그대로 둔다. */
    private record PhotoChange(boolean changed, String url) {

        static final PhotoChange UNCHANGED = new PhotoChange(false, null);

        static PhotoChange to(String url) {
            return new PhotoChange(true, url);
        }
    }

    /** 첨부 사진을 검증한다. 첨부하지 않았으면 null */
    private static UploadedPhoto parsePhoto(RoomForm form) {
        try {
            return UploadedPhoto.from(form.getPhoto());
        } catch (InvalidPhotoException exception) {
            throw new RoomCommandException(exception.getMessage());
        }
    }

    /**
     * 수정 요청의 사진 변경을 정한다. 새 사진이 있으면 교체, 없고 삭제를 골랐으면 삭제, 그 밖에는 유지한다.
     * 교체·삭제되는 이전 파일은 커밋 뒤에 지운다.
     */
    private PhotoChange photoChange(RoomEditTarget target, RoomForm form, UploadedPhoto photo) {
        String previous = target.getPhotoUrl();
        PhotoChange change;
        if (photo != null) {
            change = PhotoChange.to(storePhoto(photo));
        } else if (form.isRemovePhoto() && previous != null) {
            change = PhotoChange.to(null);
        } else {
            return PhotoChange.UNCHANGED;
        }
        if (previous != null) {
            afterCommit(() -> photoStorage.delete(previous));
        }
        return change;
    }

    /** 사진을 저장소에 올리고, 트랜잭션이 커밋되지 않으면 올린 파일을 지운다. */
    private String storePhoto(UploadedPhoto photo) {
        String url;
        try {
            url = photoStorage.store(PHOTO_DIRECTORY, photo);
        } catch (RuntimeException exception) {
            log.error("매칭방 사진 저장 실패", exception);
            throw new RoomCommandException("사진을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        photoStorage.delete(url);
                    }
                }
            });
        }
        return url;
    }

    /** 커밋 뒤에 실행한다. 트랜잭션 밖(단위 테스트)이면 바로 실행한다. */
    private static void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    /* ---------------- 공통 ---------------- */

    private static RoomCommand command(Long matchId, Long createdBy, Details details, Schedule schedule,
            String status, PhotoChange photoChange) {
        RoomCommand.RoomCommandBuilder builder = RoomCommand.builder()
                .matchId(matchId)
                .createdBy(createdBy)
                .title(details.title())
                .minParticipants(details.minParticipants())
                .maxParticipants(details.maxParticipants())
                .teamACapacity(details.teamCapacity())
                .teamBCapacity(details.teamCapacity())
                .feePerPerson(details.fee())
                .requiredSkill(details.requiredSkill())
                .status(status)
                .photoUrl(photoChange.url())
                .photoChanged(photoChange.changed());
        if (schedule != null) {
            builder.facilityId(schedule.facilityId())
                    .sportId(schedule.sportId())
                    .startAt(schedule.startAt())
                    .endAt(schedule.endAt());
        }
        return builder.build();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
