package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.kr.spoit.match.dto.MatchDetailPage;
import co.kr.spoit.match.dto.MatchDetailPage.RosterMember;
import co.kr.spoit.match.dto.MatchDetailPage.TeamMemberOption;
import co.kr.spoit.match.dto.MatchDetailPage.TeamOption;
import co.kr.spoit.match.dto.MatchDetailRow;
import co.kr.spoit.match.dto.ParticipantRow;
import co.kr.spoit.match.dto.TeamClubRow;
import co.kr.spoit.match.dto.TeamMemberRow;

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
    void teamOptionsExcludeApplicantAndMarkAppliedMembers() {
        TeamClubRow club = new TeamClubRow();
        club.setClubId(5L);
        club.setClubName("스매시클럽");
        club.setLeaderName("박민수");
        club.setMemberCount(3);
        TeamMemberRow applicant = teamMember(100L, 99L, "김채원", "동호회원", null, null, false);
        TeamMemberRow leader = teamMember(101L, 21L, "박민수", "동호회장", "1240", 3, false);
        TeamMemberRow applied = teamMember(102L, 22L, "정하늘", "동호회원", "1100", 0, true);

        List<TeamOption> options = assembler.toTeamOptions(List.of(club), List.of(applicant, leader, applied), 99L);
        MatchDetailPage page = assembler.toPage(match("모집 중"), List.of(), 99L, NOW, TODAY, options);

        assertThat(page.isHasTeams()).isTrue();
        TeamOption option = page.getClubs().get(0);
        assertThat(option.getSelectionCount()).isEqualTo(1);
        // 경기 기록이 있는 회원(박민수)만 평균에 들어간다.
        assertThat(option.getAverageRatingText()).isEqualTo("1,240");
        assertThat(option.getMembers()).extracting(TeamMemberOption::getNickname).containsExactly("박민수", "정하늘");
        assertThat(option.getMembers()).extracting(TeamMemberOption::isLeader).containsExactly(true, false);
        assertThat(option.getMembers()).extracting(TeamMemberOption::isAlreadyApplied).containsExactly(false, true);
        assertThat(option.getMembers()).extracting(TeamMemberOption::getRatingText).containsExactly("1,240", "기록 없음");
        assertThat(option.getMembers().get(0).getInitial()).isEqualTo("박");
    }

    @Test
    void noClubsDisablesTeamApply() {
        MatchDetailPage page = assembler.toPage(match("모집 중"), List.of(), 99L, NOW, TODAY);

        assertThat(page.isHasTeams()).isFalse();
        assertThat(page.getClubs()).isEmpty();
    }

    private static TeamMemberRow teamMember(long clubMemberId, long userId, String nickname, String role,
                                            String mmr, Integer ratedCount, boolean applied) {
        TeamMemberRow m = new TeamMemberRow();
        m.setClubId(5L);
        m.setClubMemberId(clubMemberId);
        m.setUserId(userId);
        m.setNickname(nickname);
        m.setRole(role);
        m.setCurrentMmr(mmr == null ? null : new BigDecimal(mmr));
        m.setRatedMatchCount(ratedCount);
        m.setAlreadyApplied(applied);
        return m;
    }

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
