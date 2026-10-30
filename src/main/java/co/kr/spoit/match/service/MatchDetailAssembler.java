package co.kr.spoit.match.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.match.dto.Fact;
import co.kr.spoit.match.dto.MatchDetailPage;
import co.kr.spoit.match.dto.MatchDetailPage.Facility;
import co.kr.spoit.match.dto.MatchDetailPage.MyApplication;
import co.kr.spoit.match.dto.MatchDetailPage.RosterMember;
import co.kr.spoit.match.dto.MatchDetailPage.TeamMemberOption;
import co.kr.spoit.match.dto.MatchDetailPage.TeamOption;
import co.kr.spoit.match.dto.MatchDetailPage.TeamRoster;
import co.kr.spoit.match.dto.MatchDetailRow;
import co.kr.spoit.match.dto.ParticipantRow;
import co.kr.spoit.match.dto.TeamClubRow;
import co.kr.spoit.match.dto.TeamMemberRow;

/**
 * 조회 결과(MatchDetailRow + 참가자)를 상세 화면용 MatchDetailPage로 변환한다.
 *
 * <p>상세 화면 문구·CSS 클래스 결정은 여기에만 둔다. (match-detail.css 기준)</p>
 */
@Component
public class MatchDetailAssembler {

    private static final String CLASS_CLOSED = "is-closed";
    private static final String CLASS_CANCELLED = "is-cancelled";

    private static final String MESSAGE_PENDING = "is-pending";
    private static final String MESSAGE_SUCCESS = "is-success";
    private static final String MESSAGE_ERROR = "is-error";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final String NO_RATING = "기록 없음";

    public MatchDetailPage toPage(MatchDetailRow row, List<ParticipantRow> participants,
                                  Long currentUserId, LocalDateTime now, LocalDate today) {
        return toPage(row, participants, currentUserId, now, today, List.of());
    }

    /** @param clubs RES-04 팀 신청에 쓸 수 있는 동호회 ({@link #toTeamOptions}) */
    public MatchDetailPage toPage(MatchDetailRow row, List<ParticipantRow> participants,
                                  Long currentUserId, LocalDateTime now, LocalDate today,
                                  List<TeamOption> clubs) {
        List<ParticipantRow> confirmed = filterStatus(participants, ParticipationStatus.CONFIRMED);
        List<ParticipantRow> pending = filterStatus(participants, ParticipationStatus.WAITING);
        long unassigned = confirmed.stream().filter(p -> p.getMatchSide() == null).count();

        int max = row.getMaxParticipants();
        int occupied = participants.size();
        int available = Math.max(0, max - occupied);
        int percent = max == 0 ? 0 : Math.min(100, occupied * 100 / max);

        boolean recruiting = MatchStatus.RECRUITING.dbValue().equals(row.getStatus());
        boolean matched = MatchStatus.MATCHED.dbValue().equals(row.getStatus());
        boolean cancelled = MatchStatus.CANCELLED.dbValue().equals(row.getStatus());
        boolean started = !row.getStartAt().isAfter(now);
        boolean joined = currentUserId != null
                && participants.stream().anyMatch(p -> currentUserId.equals(p.getUserId()));
        boolean canApply = recruiting && available > 0 && !started && !joined;

        String statusLabel;
        String statusClass;
        if (matched) {
            statusLabel = "팀 배정 완료";
            statusClass = "";
        } else if (recruiting && available == 0) {
            statusLabel = "정원 마감";
            statusClass = CLASS_CLOSED;
        } else if (recruiting) {
            statusLabel = "모집 중";
            statusClass = "";
        } else {
            statusLabel = row.getStatus();
            statusClass = cancelled ? CLASS_CANCELLED : CLASS_CLOSED;
        }

        ApplyMessage message = applyMessage(recruiting, matched, cancelled, started, joined, available);

        String dateLabel = MatchCardAssembler.dateLabel(row.getStartAt().toLocalDate(), today);
        String time = row.getStartAt().format(TIME) + "~" + row.getEndAt().format(TIME);
        String feeText = MatchCardAssembler.formatFee(row.getFeePerPerson());

        // 팀 배정 전 확정 참가자는 이름 없이 인원만 안내한다.
        String caption = available == 0 ? "정원 마감" : available + "자리 남음";
        if (unassigned > 0) {
            caption += " · " + unassigned + "명 팀 배정 대기";
        }

        return MatchDetailPage.builder()
                .matchId(row.getMatchId())
                .title(row.getTitle())
                .statusLabel(statusLabel)
                .statusClass(statusClass)
                .sportName(row.getSportName())
                .place(row.getFacilityName())
                .dateLabel(dateLabel)
                .time(time)
                .feeText(feeText)
                .levelLabel(row.getRequiredSkill())
                .facts(List.of(
                        new Fact("일시", dateLabel + " " + time),
                        new Fact("종목", row.getSportName()),
                        new Fact("실력 조건", row.getRequiredSkill()),
                        new Fact("모집 인원", recruitText(row.getMinParticipants(), max)),
                        new Fact("팀 구성", "A팀 " + row.getTeamACapacity() + "명 · B팀 " + row.getTeamBCapacity() + "명"),
                        new Fact("참가비", feeText + "원 / 1인"),
                        new Fact("신청 방식", "개인 신청 · 팀 대표 신청")))
                .note(matched
                        ? "팀 배정이 완료된 매칭방입니다."
                        : "최소 " + row.getMinParticipants() + "명이 모이면 경기가 진행되며, 정원이 차면 A·B팀으로 배정됩니다.")
                .facility(new Facility(
                        null, // 시설 사진 컬럼 없음 → 종목명 fallback (RES-01과 동일)
                        row.getFacilityName(),
                        row.getSportName(),
                        row.getFacilityName() + " · " + row.getRegionName(),
                        true,
                        List.of(
                                new Fact("시설명", row.getFacilityName()),
                                new Fact("지역", row.getRegionName()),
                                new Fact("주소", row.getAddress()))))
                .confirmedCount(confirmed.size())
                .pendingConfirmationCount(pending.size())
                .maxParticipants(max)
                .occupiedSeats(occupied)
                .availableSeats(available)
                .occupancyPercent(percent)
                .occupancyLabel(occupied + " / " + max + "명")
                .occupancyCaption(caption)
                .ratingNote("신청 시점 MMR 기준")
                .teamA(roster(confirmed, "A", row.getCaptainAParticipantId(), row))
                .teamB(roster(confirmed, "B", row.getCaptainBParticipantId(), row))
                .pendingParticipants(pending.stream()
                        .map(p -> new RosterMember(p.getNickname(), isHost(p, row), "신청 확인 대기"))
                        .toList())
                .applyMessage(message.text())
                .applyMessageClass(message.cssClass())
                .myApplication(MyApplication.HIDDEN)
                .canApply(canApply)
                .hasTeams(!clubs.isEmpty())
                .clubs(clubs)
                .build();
    }

    /**
     * RES-04 팀 신청 다이얼로그용 동호회·회원 목록. 신청 대표 본인은 회원 목록에서 빼고
     * 다이얼로그 위쪽 고정 칸에 따로 보여 준다.
     */
    public List<TeamOption> toTeamOptions(List<TeamClubRow> clubs, List<TeamMemberRow> members, long applicantUserId) {
        Map<Long, List<TeamMemberRow>> byClub = members.stream()
                .collect(Collectors.groupingBy(TeamMemberRow::getClubId));
        return clubs.stream().map(club -> {
            List<TeamMemberRow> all = byClub.getOrDefault(club.getClubId(), List.of());
            List<TeamMemberOption> options = all.stream()
                    .filter(m -> m.getUserId() != applicantUserId)
                    .map(m -> new TeamMemberOption(m.getClubMemberId(), m.getNickname(), initial(m.getNickname()),
                            m.isLeader(), ratingText(m), m.isAlreadyApplied()))
                    .toList();
            return new TeamOption(club.getClubId(), club.getClubName(),
                    club.getLeaderName() == null ? "없음" : club.getLeaderName(),
                    club.getMemberCount(), averageRating(all), 1, options);
        }).toList();
    }

    /** 확정 경기 기록이 있는 회원만 MMR을 보여 준다 (마이페이지와 같은 기준). */
    private String ratingText(TeamMemberRow m) {
        return rated(m) ? formatMmr(m.getCurrentMmr()) : NO_RATING;
    }

    private String averageRating(List<TeamMemberRow> members) {
        List<BigDecimal> values = members.stream().filter(this::rated).map(TeamMemberRow::getCurrentMmr).toList();
        if (values.isEmpty()) {
            return NO_RATING;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return formatMmr(sum.divide(BigDecimal.valueOf(values.size()), 0, RoundingMode.HALF_UP));
    }

    private boolean rated(TeamMemberRow m) {
        return m.getCurrentMmr() != null && m.getRatedMatchCount() != null && m.getRatedMatchCount() > 0;
    }

    private static String initial(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "?";
        }
        String trimmed = nickname.strip();
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1));
    }

    private ApplyMessage applyMessage(boolean recruiting, boolean matched, boolean cancelled,
                                      boolean started, boolean joined, int available) {
        if (joined) {
            return new ApplyMessage("이미 참가 신청한 매칭방입니다. 신청 내역은 마이페이지에서 확인할 수 있습니다.", MESSAGE_SUCCESS);
        }
        if (cancelled) {
            return new ApplyMessage("취소된 매칭방입니다.", MESSAGE_ERROR);
        }
        if (matched) {
            return new ApplyMessage("팀 배정이 완료되어 추가 신청을 받지 않습니다.", MESSAGE_PENDING);
        }
        if (!recruiting || started) {
            return new ApplyMessage("신청 기간이 지난 매칭방입니다.", MESSAGE_ERROR);
        }
        if (available == 0) {
            return new ApplyMessage("정원이 모두 찼습니다.", MESSAGE_ERROR);
        }
        return new ApplyMessage(available + "자리 남았습니다. 참가 방식을 선택해 신청해 주세요.", "");
    }

    /** 최소·최대가 같으면 "6명", 다르면 "4~8명" */
    private String recruitText(int min, int max) {
        return min == max ? max + "명" : min + "~" + max + "명";
    }

    private TeamRoster roster(List<ParticipantRow> confirmed, String side, Long captainId, MatchDetailRow row) {
        List<ParticipantRow> members = confirmed.stream().filter(p -> side.equals(p.getMatchSide())).toList();
        List<RosterMember> views = members.stream()
                .map(p -> new RosterMember(p.getNickname(), isHost(p, row), memberMeta(p, captainId)))
                .toList();
        return new TeamRoster(averageMmr(members), views);
    }

    private String memberMeta(ParticipantRow p, Long captainId) {
        String mmr = p.getEntryMmr() == null ? "기록 없음" : "MMR " + formatMmr(p.getEntryMmr());
        return Objects.equals(p.getParticipantId(), captainId) ? "팀장 · " + mmr : mmr;
    }

    /** 팀 평균 MMR, 인원이 없으면 "기록 없음" */
    private String averageMmr(List<ParticipantRow> members) {
        List<BigDecimal> values = members.stream().map(ParticipantRow::getEntryMmr).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return "기록 없음";
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return formatMmr(sum.divide(BigDecimal.valueOf(values.size()), 0, RoundingMode.HALF_UP));
    }

    private String formatMmr(BigDecimal value) {
        return new DecimalFormat("#,##0").format(value);
    }

    private boolean isHost(ParticipantRow p, MatchDetailRow row) {
        return Objects.equals(p.getUserId(), row.getCreatedBy());
    }

    private List<ParticipantRow> filterStatus(List<ParticipantRow> list, ParticipationStatus status) {
        return list.stream().filter(p -> status.dbValue().equals(p.getParticipationStatus())).toList();
    }

    private record ApplyMessage(String text, String cssClass) {
    }
}
