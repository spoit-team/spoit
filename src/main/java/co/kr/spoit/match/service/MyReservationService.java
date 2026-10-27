package co.kr.spoit.match.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.ApplicationType;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.match.dto.MyApplicationRow;
import co.kr.spoit.match.dto.MyReservations;
import co.kr.spoit.match.dto.MyReservations.Entry;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import lombok.RequiredArgsConstructor;

/**
 * RES-06 마이페이지 신청 내역.
 *
 * <p>현재 신청: 경기가 끝나기 전인 대기·확정 신청. 지난 참여·취소: 그 밖의 모든 신청
 * (이용 완료, 신청 취소·반려, 매칭방 취소, 확정되지 않고 지난 신청).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyReservationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /* 상태 색: styles.css에 정의된 텍스트 색만 쓴다. */
    private static final String CLASS_WAITING = "text-book-ink";
    private static final String CLASS_CONFIRMED = "text-match-ink";
    private static final String CLASS_DONE = "text-ink-2";
    private static final String CLASS_INACTIVE = "text-muted";

    private final MatchApplicationMapper applicationMapper;

    public MyReservations find(long userId) {
        return find(userId, LocalDateTime.now(ZONE));
    }

    /** 테스트에서 기준 시각을 고정할 수 있도록 분리했다. */
    MyReservations find(long userId, LocalDateTime now) {
        List<MyApplicationRow> rows =
                applicationMapper.findMyApplications(userId, ParticipationStatus.occupyingValues());
        LocalDate today = now.toLocalDate();

        List<Entry> current = new ArrayList<>();
        List<Entry> past = new ArrayList<>();
        int confirmed = 0;
        int completed = 0;
        int waiting = 0;

        for (MyApplicationRow row : rows) {
            State state = classify(row, now);
            Entry entry = toEntry(row, state, today);
            if (state.current) {
                current.add(entry);
            } else {
                past.add(entry);
            }
            switch (state) {
                case WAITING -> waiting++;
                case CONFIRMED -> confirmed++;
                case COMPLETED -> {
                    confirmed++;
                    completed++;
                }
                default -> {
                }
            }
        }
        // 조회는 경기 시작 순: 현재 신청은 가까운 경기부터, 지난 내역은 최근 경기부터
        Collections.reverse(past);
        return new MyReservations(current, past, confirmed, completed, waiting);
    }

    State classify(MyApplicationRow row, LocalDateTime now) {
        String status = row.getStatus();
        boolean ended = !row.getEndAt().isAfter(now);
        if (ApplicationStatus.CANCELLED.dbValue().equals(status)) {
            return State.CANCELLED;
        }
        if (ApplicationStatus.REJECTED.dbValue().equals(status)) {
            return State.REJECTED;
        }
        if (MatchStatus.CANCELLED.dbValue().equals(row.getMatchStatus())) {
            return State.MATCH_CANCELLED;
        }
        if (ApplicationStatus.WAITING.dbValue().equals(status)) {
            return ended ? State.EXPIRED : State.WAITING;
        }
        boolean closed = MatchStatus.CLOSED.dbValue().equals(row.getMatchStatus());
        return ended || closed ? State.COMPLETED : State.CONFIRMED;
    }

    private Entry toEntry(MyApplicationRow row, State state, LocalDate today) {
        boolean individual = ApplicationType.INDIVIDUAL.dbValue().equals(row.getApplicationType());
        String metaLine = MatchCardAssembler.dateLabel(row.getStartAt().toLocalDate(), today)
                + " " + row.getStartAt().format(TIME) + "~" + row.getEndAt().format(TIME)
                + " · " + row.getFacilityName();
        String seatLine;
        if (individual) {
            seatLine = "참가 1명";
        } else {
            seatLine = row.getParticipantCount() > 0 ? "참가 " + row.getParticipantCount() + "명" : "참가 인원 확정 전";
        }
        return new Entry(
                row.getApplicationId(),
                row.getMatchId(),
                row.getSportName(),
                individual ? "개인 신청" : "팀 신청",
                state.label,
                state.cssClass,
                row.getTitle(),
                metaLine,
                seatLine,
                statusNote(row, state),
                false,
                "신청 취소",
                false,
                null);
    }

    private String statusNote(MyApplicationRow row, State state) {
        return switch (state) {
            case WAITING -> "시설 관리자가 확인하면 참가가 확정됩니다.";
            case CONFIRMED -> row.getMatchSide() == null ? "팀 배정 전입니다." : row.getMatchSide() + "팀에 배정되었습니다.";
            case COMPLETED -> row.getMatchSide() == null ? null : row.getMatchSide() + "팀으로 참가했습니다.";
            case MATCH_CANCELLED -> "매칭방이 취소되었습니다.";
            case EXPIRED -> "경기 전까지 참가가 확정되지 않았습니다.";
            default -> null;
        };
    }

    enum State {
        WAITING(true, "확정 대기", CLASS_WAITING),
        CONFIRMED(true, "참가 확정", CLASS_CONFIRMED),
        COMPLETED(false, "이용 완료", CLASS_DONE),
        CANCELLED(false, "신청 취소", CLASS_INACTIVE),
        REJECTED(false, "신청 반려", CLASS_INACTIVE),
        MATCH_CANCELLED(false, "매치 취소", CLASS_INACTIVE),
        EXPIRED(false, "미확정 종료", CLASS_INACTIVE);

        private final boolean current;
        private final String label;
        private final String cssClass;

        State(boolean current, String label, String cssClass) {
            this.current = current;
            this.label = label;
            this.cssClass = cssClass;
        }
    }
}
