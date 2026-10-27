package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import co.kr.spoit.mat.domain.TeamCandidate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamCaptainSelectorTest {

    private final TeamCaptainSelector selector = new TeamCaptainSelector();

    @Test
    void selectsHighestMmrRegardlessOfOrder() {
        var members = List.of(
                candidate(3L, "998.10"),
                candidate(7L, "1043.20"),
                candidate(1L, "1012.00"));

        assertThat(selector.selectCaptain(members)).isEqualTo(7L);
        assertThat(selector.selectCaptain(reversed(members))).isEqualTo(7L);
    }

    @Test
    void selectsSmallestParticipantIdWhenHighestMmrIsTied() {
        var members = List.of(
                candidate(9L, "1050.00"),
                candidate(4L, "1050.00"),
                candidate(2L, "990.00"),
                candidate(6L, "1050.00"));

        assertThat(selector.selectCaptain(members)).isEqualTo(4L);
        assertThat(selector.selectCaptain(reversed(members))).isEqualTo(4L);
    }

    @Test
    void treatsSameMmrWithDifferentScaleAsTie() {
        var members = List.of(candidate(9L, "1000.5"), candidate(4L, "1000.50"));

        assertThat(selector.selectCaptain(members)).isEqualTo(4L);
    }

    @Test
    void singleMemberBecomesCaptain() {
        assertThat(selector.selectCaptain(List.of(candidate(5L, "1000")))).isEqualTo(5L);
    }

    @Test
    void rejectsNullOrEmptyTeam() {
        assertThatIllegalArgumentException().isThrownBy(() -> selector.selectCaptain(null));
        assertThatIllegalArgumentException().isThrownBy(() -> selector.selectCaptain(List.of()));
    }

    @Test
    void rejectsNullMember() {
        var members = Arrays.asList(candidate(1L, "1000"), null);

        assertThatIllegalArgumentException().isThrownBy(() -> selector.selectCaptain(members));
    }

    @Test
    void rejectsDuplicateParticipantId() {
        var members = List.of(candidate(1L, "1000"), candidate(1L, "1100"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> selector.selectCaptain(members))
                .withMessageContaining("중복");
    }

    private TeamCandidate candidate(Long participantId, String mmr) {
        return new TeamCandidate(participantId, new BigDecimal(mmr));
    }

    private List<TeamCandidate> reversed(List<TeamCandidate> members) {
        var copy = new ArrayList<>(members);
        Collections.reverse(copy);
        return copy;
    }
}
