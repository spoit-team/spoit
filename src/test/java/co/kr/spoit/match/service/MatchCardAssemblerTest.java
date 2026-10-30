package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.kr.spoit.match.dto.MatchCardView;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.ParticipantRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MatchCardAssemblerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 12, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private final MatchCardAssembler assembler = new MatchCardAssembler();

    @Test
    void statusLabelAndCssClassFollowMatchStatus() {
        MatchCardView matched = assembler.toCard(match("매칭 완료"), List.of(), NOW, TODAY);
        assertThat(matched.getStatusLabel()).isEqualTo("팀 배정 완료");
        assertThat(matched.getStatusClass()).isEqualTo("is-matched");

        MatchCardView full = assembler.toCard(
                match("모집 중"), participants(4), NOW, TODAY);
        assertThat(full.getStatusLabel()).isEqualTo("정원 마감");
        assertThat(full.getStatusClass()).isEqualTo("is-full");

        MatchCardView recruiting = assembler.toCard(
                match("모집 중"), participants(2), NOW, TODAY);
        assertThat(recruiting.getStatusLabel()).isEqualTo("모집 중");
        assertThat(recruiting.getStatusClass()).isEqualTo("is-recruiting");

        MatchCardView closed = assembler.toCard(match("종료"), List.of(), NOW, TODAY);
        assertThat(closed.getStatusLabel()).isEqualTo("종료");
        assertThat(closed.getStatusClass()).isEqualTo("is-closed");
    }

    @Test
    void occupancyPercentCapsAtHundredAndHandlesZeroCapacity() {
        MatchRow overfull = match("모집 중");
        overfull.setMaxParticipants(2);

        assertThat(assembler.toCard(overfull, participants(3), NOW, TODAY).getOccupiedPercent()).isEqualTo(100);

        MatchRow noCapacity = match("모집 중");
        noCapacity.setMaxParticipants(0);

        assertThat(assembler.toCard(noCapacity, List.of(), NOW, TODAY).getOccupiedPercent()).isZero();
    }

    private MatchRow match(String status) {
        MatchRow row = new MatchRow();
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
        row.setFeePerPerson(new BigDecimal("5000"));
        row.setRequiredSkill("초급");
        row.setStatus(status);
        return row;
    }

    private List<ParticipantRow> participants(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> {
                    ParticipantRow p = new ParticipantRow();
                    p.setParticipantId((long) i);
                    p.setMatchId(1L);
                    p.setUserId(10L + i);
                    p.setNickname("회원" + i);
                    p.setParticipationStatus("확정");
                    return p;
                })
                .toList();
    }
}
