package co.kr.spoit.match.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.match.dto.Fact;
import co.kr.spoit.match.dto.MatchCardView;
import co.kr.spoit.match.dto.MatchDetailView;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.ParticipantRow;
import co.kr.spoit.match.dto.ParticipantView;

/**
 * 조회 결과(MatchRow + 참가자)를 템플릿용 MatchCardView로 변환한다.
 *
 * <p>화면 문구·CSS 클래스 결정은 여기에만 둔다.</p>
 */
@Component
public class MatchCardAssembler {

    /** 아바타로 보여줄 최대 인원 */
    static final int AVATAR_LIMIT = 4;

    // TODO playon-matches.css의 실제 상태 클래스명과 맞출 것
    private static final String CLASS_RECRUITING = "is-recruiting";
    private static final String CLASS_FULL = "is-full";
    private static final String CLASS_MATCHED = "is-matched";
    private static final String CLASS_CLOSED = "is-closed";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public MatchCardView toCard(MatchRow row, List<ParticipantRow> participants,
                                LocalDateTime now, LocalDate today) {
        List<ParticipantRow> confirmed = filterStatus(participants, ParticipationStatus.CONFIRMED);
        List<ParticipantRow> pending = filterStatus(participants, ParticipationStatus.WAITING);

        int max = row.getMaxParticipants();
        int occupied = participants.size();
        int remaining = Math.max(0, max - occupied);
        int percent = occupancyPercent(occupied, max);

        boolean recruiting = MatchStatus.RECRUITING.dbValue().equals(row.getStatus());
        boolean matched = MatchStatus.MATCHED.dbValue().equals(row.getStatus());
        boolean canApply = recruiting && remaining > 0 && row.getStartAt().isAfter(now);

        String statusLabel = commonStatusLabel(row.getStatus(), remaining);
        String statusClass;
        if (matched) {
            statusClass = CLASS_MATCHED;
        } else if (recruiting && remaining == 0) {
            statusClass = CLASS_FULL;
        } else if (recruiting) {
            statusClass = CLASS_RECRUITING;
        } else {
            statusClass = CLASS_CLOSED;
        }

        String actionLabel = matched ? "팀 배정 완료" : (remaining == 0 ? "정원 마감" : "예약 마감");

        List<ParticipantView> all = toViews(participants, row);
        List<ParticipantView> teamA = toViews(filterSide(participants, "A"), row);
        List<ParticipantView> teamB = toViews(filterSide(participants, "B"), row);

        String dateLabel = dateLabel(row.getStartAt().toLocalDate(), today);
        String time = row.getStartAt().format(TIME) + "~" + row.getEndAt().format(TIME);
        String feeText = formatFee(row.getFeePerPerson());

        MatchDetailView detail = MatchDetailView.builder()
                .badge(statusLabel)
                .title(row.getTitle())
                .facilityName(row.getFacilityName())
                .facts(List.of(
                        new Fact("일시", dateLabel + " " + time),
                        new Fact("종목", row.getSportName()),
                        new Fact("실력 조건", row.getRequiredSkill()),
                        new Fact("모집 인원", row.getMinParticipants() + "~" + max + "명"),
                        new Fact("참가비", feeText + "원"),
                        new Fact("신청 방식", "개인 신청 · 팀 대표 신청")))
                .note(matched
                        ? "팀 배정이 완료된 매칭방입니다."
                        : "최소 " + row.getMinParticipants() + "명이 모이면 경기가 진행됩니다.")
                .facilityVisible(true)
                .facilityFacts(List.of(
                        new Fact("시설명", row.getFacilityName()),
                        new Fact("지역", row.getRegionName()),
                        new Fact("주소", row.getAddress())))
                .matched(matched)
                .teamACount(teamA.size())
                .teamBCount(teamB.size())
                .teamA(teamA)
                .teamB(teamB)
                .participants(toViews(confirmed, row))
                .pendingParticipants(toViews(pending, row))
                .canJoin(canApply)
                .build();

        return MatchCardView.builder()
                .matchId(row.getMatchId())
                .title(row.getTitle())
                .sportName(row.getSportName())
                .photoUrl(null) // 시설 사진 컬럼 없음 → 종목명 fallback 표시
                .photoAlt(row.getFacilityName())
                .facilityDistrictName(row.getRegionName())
                .levelLabel(row.getRequiredSkill())
                .statusLabel(statusLabel)
                .statusClass(statusClass)
                .dateLabel(dateLabel)
                .time(time)
                .place(row.getFacilityName())
                .feeText(feeText)
                .avatarParticipants(all.subList(0, Math.min(AVATAR_LIMIT, all.size())))
                .extraParticipantCount(Math.max(0, all.size() - AVATAR_LIMIT))
                .participantCount(confirmed.size())
                .pendingConfirmationCount(pending.size())
                .occupiedSeats(occupied)
                .maxParticipants(max)
                .remainingSeats(remaining)
                .occupiedPercent(percent)
                .participantSummary(confirmed.size() + "명 확정 / " + max + "명")
                .remainingLabel(remaining == 0 ? "마감" : remaining + "자리 남음")
                .showTeams(matched)
                .teamACount(teamA.size())
                .teamBCount(teamB.size())
                .canApply(canApply)
                .actionLabel(actionLabel)
                .detail(detail)
                .build();
    }

    /** 오늘, 내일, 10/24(토) */
    static String dateLabel(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return "오늘";
        }
        if (date.equals(today.plusDays(1))) {
            return "내일";
        }
        return date.format(DATE) + "(" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }

    private List<ParticipantRow> filterSide(List<ParticipantRow> list, String side) {
        return list.stream().filter(p -> side.equals(p.getMatchSide())).toList();
    }

    /** 확정·대기 참가자를 상태 값으로 나눈다. 카드와 상세가 같은 기준을 쓴다. */
    static List<ParticipantRow> filterStatus(List<ParticipantRow> list, ParticipationStatus status) {
        return list.stream().filter(p -> status.dbValue().equals(p.getParticipationStatus())).toList();
    }

    /** 카드·상세 공통 상태 문구. CSS 클래스는 화면마다 달라 각자 정한다. */
    static String commonStatusLabel(String status, int remaining) {
        if (MatchStatus.MATCHED.dbValue().equals(status)) {
            return "팀 배정 완료";
        }
        if (MatchStatus.RECRUITING.dbValue().equals(status)) {
            return remaining == 0 ? "정원 마감" : "모집 중";
        }
        return status;
    }

    /** 점유 퍼센트(0~100, 정수 나눗셈). 정원이 0이면 0. 카드와 상세가 같은 식을 쓴다. */
    static int occupancyPercent(int occupied, int max) {
        return max == 0 ? 0 : Math.min(100, occupied * 100 / max);
    }

    private List<ParticipantView> toViews(List<ParticipantRow> list, MatchRow row) {
        return list.stream()
                .map(p -> new ParticipantView(p.getNickname(), initial(p.getNickname()),
                        Objects.equals(p.getUserId(), row.getCreatedBy())))
                .toList();
    }

    /** 닉네임 첫 글자 (이모지 등 서로게이트 문자도 안전하게) */
    private String initial(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "?";
        }
        String trimmed = nickname.strip();
        return new String(Character.toChars(trimmed.codePointAt(0)));
    }

    static String formatFee(BigDecimal fee) {
        return new DecimalFormat("#,##0").format(fee == null ? BigDecimal.ZERO : fee);
    }
}
