package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.kr.spoit.match.dto.MatchDetailPage;
import co.kr.spoit.match.dto.MatchDetailPage.RosterMember;
import co.kr.spoit.match.dto.MatchDetailRow;
import co.kr.spoit.match.dto.ParticipantRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchDetailAssemblerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 12, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private final MatchDetailAssembler assembler = new MatchDetailAssembler();

    @Test
    void recruitingMatchCanApplyAndHidesUnassignedNames() {
        List<ParticipantRow> participants = List.of(
                participant(1L, 10L, "확정", null, "1000"),
                participant(2L, 11L, "대기", null, "1000"));

        MatchDetailPage page = assembler.toPage(match("모집 중"), participants, 99L, NOW, TODAY);

        assertThat(page.isCanApply()).isTrue();
        assertThat(page.getStatusLabel()).isEqualTo("모집 중");
        assertThat(page.getConfirmedCount()).isEqualTo(1);
        assertThat(page.getPendingConfirmationCount()).isEqualTo(1);
        assertThat(page.getOccupancyLabel()).isEqualTo("2 / 4명");
        assertThat(page.getOccupancyCaption()).isEqualTo("2자리 남음 · 1명 팀 배정 대기");
        assertThat(page.getTeamA().getMembers()).isEmpty();
        assertThat(page.getTeamA().getAverageText()).isEqualTo("기록 없음");
        assertThat(page.getPendingParticipants()).extracting(RosterMember::getNickname).containsExactly("회원2");
        assertThat(page.getMyApplication().isVisible()).isFalse();
        assertThat(page.isHasTeams()).isFalse();
    }

    @Test
    void matchedMatchShowsTeamsWithCaptainAndAverage() {
        List<ParticipantRow> participants = List.of(
                participant(1L, 10L, "확정", "A", "1010"),
                participant(2L, 11L, "확정", "A", "991"),
                participant(3L, 12L, "확정", "B", "1000"),
                participant(4L, 13L, "확정", "B", "1000"));

        MatchDetailPage page = assembler.toPage(match("매칭 완료"), participants, null, NOW, TODAY);

        assertThat(page.isCanApply()).isFalse();
        assertThat(page.getStatusLabel()).isEqualTo("팀 배정 완료");
        assertThat(page.getApplyMessageClass()).isEqualTo("is-pending");
        assertThat(page.getTeamA().getAverageText()).isEqualTo("1,001");
        assertThat(page.getTeamA().getMembers()).extracting(RosterMember::getMeta)
                .containsExactly("팀장 · MMR 1,010", "MMR 991");
        assertThat(page.getTeamA().getMembers().get(0).isHost()).isTrue();
        assertThat(page.getOccupancyCaption()).isEqualTo("정원 마감");
    }

    @Test
    void alreadyJoinedMemberCannotApplyAgain() {
        List<ParticipantRow> participants = List.of(participant(1L, 10L, "확정", null, "1000"));

        MatchDetailPage page = assembler.toPage(match("모집 중"), participants, 10L, NOW, TODAY);

        assertThat(page.isCanApply()).isFalse();
        assertThat(page.getApplyMessageClass()).isEqualTo("is-success");
    }

    @Test
    void startedMatchCannotApply() {
        MatchDetailRow row = match("모집 중");
        row.setStartAt(NOW.minusMinutes(1));

        MatchDetailPage page = assembler.toPage(row, List.of(), null, NOW, TODAY);

        assertThat(page.isCanApply()).isFalse();
        assertThat(page.getApplyMessage()).isEqualTo("신청 기간이 지난 매칭방입니다.");
    }

    private MatchDetailRow match(String status) {
        MatchDetailRow row = new MatchDetailRow();
        row.setMatchId(1L);
        row.setTitle("저녁 배드민턴");
        row.setCreatedBy(10L);
        row.setSportId(1L);
        row.setSportName("배드민턴");
        row.setFacilityName("강남 체육관");
        row.setRegionName("강남구");
        row.setAddress("서울 강남구");
        row.setStartAt(NOW.plusDays(1));
        row.setEndAt(NOW.plusDays(1).plusHours(2));
        row.setMinParticipants(2);
        row.setMaxParticipants(4);
        row.setTeamACapacity(2);
        row.setTeamBCapacity(2);
        row.setFeePerPerson(new BigDecimal("5000"));
        row.setRequiredSkill("초급");
        row.setStatus(status);
        row.setCaptainAParticipantId(1L);
        return row;
    }

    private ParticipantRow participant(Long participantId, Long userId, String status, String side, String mmr) {
        ParticipantRow p = new ParticipantRow();
        p.setParticipantId(participantId);
        p.setMatchId(1L);
        p.setUserId(userId);
        p.setNickname("회원" + participantId);
        p.setParticipationStatus(status);
        p.setMatchSide(side);
        p.setEntryMmr(new BigDecimal(mmr));
        return p;
    }
}
