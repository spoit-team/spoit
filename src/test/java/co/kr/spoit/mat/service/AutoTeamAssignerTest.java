package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import co.kr.spoit.mat.domain.TeamAssignment;
import co.kr.spoit.mat.domain.TeamCandidate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AutoTeamAssignerTest {

    private final AutoTeamAssigner assigner = new AutoTeamAssigner(new TeamCaptainSelector());

    @Test
    void assignsFourVersusFourWithMinimumDifference() {
        // 가능한 35가지 구성 중 차이 6.99가 유일한 최솟값이다(jshell 전수 계산으로 확인).
        var participants = List.of(
                candidate(101L, "1012.34"),
                candidate(102L, "987.65"),
                candidate(103L, "1045.10"),
                candidate(104L, "968.20"),
                candidate(105L, "1003.00"),
                candidate(106L, "1020.75"),
                candidate(107L, "955.40"),
                candidate(108L, "1001.55"));

        TeamAssignment result = assigner.assign(participants, 4);

        assertThat(result.teamAParticipantIds()).containsExactly(101L, 102L, 103L, 107L);
        assertThat(result.teamBParticipantIds()).containsExactly(104L, 105L, 106L, 108L);
        assertThat(result.teamAMmrSum()).isEqualTo(new BigDecimal("4000.49"));
        assertThat(result.teamBMmrSum()).isEqualTo(new BigDecimal("3993.50"));
        assertThat(result.teamAMmrAverage()).isEqualTo(new BigDecimal("1000.12"));
        // 998.375 -> HALF_UP
        assertThat(result.teamBMmrAverage()).isEqualTo(new BigDecimal("998.38"));
        assertThat(result.mmrDifference()).isEqualTo(new BigDecimal("6.99"));
        assertThat(result.captainAParticipantId()).isEqualTo(103L);
        assertThat(result.captainBParticipantId()).isEqualTo(106L);
        assertMatchesOracle(participants, 4, result);
    }

    @Test
    void picksLexicographicallyFirstSplitAmongZeroDifferenceSplits() {
        // 합 4100.00이 되는 A팀이 여럿이고, 그중 A팀 ID 목록이 사전순으로 가장 앞선 구성은 1,2,7,8이다.
        var participants = List.of(
                candidate(1L, "1200"),
                candidate(2L, "1150"),
                candidate(3L, "1100"),
                candidate(4L, "1050"),
                candidate(5L, "1000"),
                candidate(6L, "950"),
                candidate(7L, "900"),
                candidate(8L, "850"));

        TeamAssignment result = assigner.assign(participants, 4);

        assertThat(result.teamAParticipantIds()).containsExactly(1L, 2L, 7L, 8L);
        assertThat(result.teamBParticipantIds()).containsExactly(3L, 4L, 5L, 6L);
        assertThat(result.teamAMmrSum()).isEqualTo(new BigDecimal("4100.00"));
        assertThat(result.teamBMmrSum()).isEqualTo(new BigDecimal("4100.00"));
        assertThat(result.mmrDifference()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.captainAParticipantId()).isEqualTo(1L);
        assertThat(result.captainBParticipantId()).isEqualTo(3L);
        assertMatchesOracle(participants, 4, result);
    }

    @Test
    void allEqualMmrPutsSmallestIdsInTeamAAndSmallestIdsBecomeCaptains() {
        var participants = List.of(
                candidate(26L, "1000"),
                candidate(21L, "1000"),
                candidate(24L, "1000"),
                candidate(23L, "1000"),
                candidate(25L, "1000"),
                candidate(22L, "1000"));

        TeamAssignment result = assigner.assign(participants, 3);

        assertThat(result.teamAParticipantIds()).containsExactly(21L, 22L, 23L);
        assertThat(result.teamBParticipantIds()).containsExactly(24L, 25L, 26L);
        assertThat(result.mmrDifference()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.teamAMmrAverage()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(result.captainAParticipantId()).isEqualTo(21L);
        assertThat(result.captainBParticipantId()).isEqualTo(24L);
    }

    @Test
    void returnsSameResultForAnyInputOrder() {
        var random = new Random(20261026L);
        var participants = randomParticipants(random, 10, 90000, 115000);
        TeamAssignment expected = assigner.assign(participants, 5);

        for (int i = 0; i < 20; i++) {
            var shuffled = new ArrayList<>(participants);
            Collections.shuffle(shuffled, random);

            assertThat(assigner.assign(shuffled, 5)).isEqualTo(expected);
        }
    }

    @Test
    void treatsSameMmrWithDifferentScaleAsSameInput() {
        var plain = List.of(candidate(1L, "1000.5"), candidate(2L, "999"), candidate(3L, "1001"), candidate(4L, "1000.5"));
        var scaled = List.of(candidate(1L, "1000.50"), candidate(2L, "999.00"), candidate(3L, "1001.00"),
                candidate(4L, "1000.500"));

        assertThat(assigner.assign(plain, 2)).isEqualTo(assigner.assign(scaled, 2));
    }

    @Test
    void selectsHighestMmrOfEachTeamAsCaptain() {
        // 최적 구성: A {1, 4} = 2000.00, B {2, 3} = 2000.00
        var participants = List.of(
                candidate(1L, "900"),
                candidate(2L, "950"),
                candidate(3L, "1050"),
                candidate(4L, "1100"));

        TeamAssignment result = assigner.assign(participants, 2);

        assertThat(result.teamAParticipantIds()).containsExactly(1L, 4L);
        assertThat(result.captainAParticipantId()).isEqualTo(4L);
        assertThat(result.captainBParticipantId()).isEqualTo(3L);
    }

    @Test
    void breaksCaptainTieBySmallestParticipantIdWithinEachTeam() {
        // 최적 구성: A {1, 2, 5} = 3000.00, B {3, 4, 6} = 3000.00. 양 팀 모두 1100점 동점자가 있다.
        var participants = List.of(
                candidate(6L, "800"),
                candidate(5L, "800"),
                candidate(4L, "1100"),
                candidate(3L, "1100"),
                candidate(2L, "1100"),
                candidate(1L, "1100"));

        TeamAssignment result = assigner.assign(participants, 3);

        assertThat(result.teamAParticipantIds()).containsExactly(1L, 2L, 5L);
        assertThat(result.teamBParticipantIds()).containsExactly(3L, 4L, 6L);
        assertThat(result.captainAParticipantId()).isEqualTo(1L);
        assertThat(result.captainBParticipantId()).isEqualTo(3L);
    }

    @Test
    void oneVersusOneAssignsSmallestIdToTeamA() {
        TeamAssignment result = assigner.assign(List.of(candidate(8L, "1200"), candidate(3L, "990.10")), 1);

        assertThat(result.teamAParticipantIds()).containsExactly(3L);
        assertThat(result.teamBParticipantIds()).containsExactly(8L);
        assertThat(result.mmrDifference()).isEqualTo(new BigDecimal("209.90"));
        assertThat(result.captainAParticipantId()).isEqualTo(3L);
        assertThat(result.captainBParticipantId()).isEqualTo(8L);
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 9})
    void rejectsParticipantCountOtherThanTwiceTheCapacity(int count) {
        var participants = sequentialParticipants(count);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> assigner.assign(participants, 4))
                .withMessageContaining("8명");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 12})
    void rejectsCapacityOutsideOneToEleven(int capacity) {
        var participants = sequentialParticipants(Math.max(0, capacity * 2));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> assigner.assign(participants, capacity))
                .withMessageContaining("1~11명");
    }

    @Test
    void rejectsDuplicateParticipantId() {
        var participants = List.of(
                candidate(1L, "1000"), candidate(2L, "1000"), candidate(2L, "1100"), candidate(3L, "1000"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> assigner.assign(participants, 2))
                .withMessageContaining("중복");
    }

    @Test
    void rejectsNullListOrNullParticipant() {
        assertThatIllegalArgumentException().isThrownBy(() -> assigner.assign(null, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assigner.assign(Arrays.asList(candidate(1L, "1000"), null), 1));
    }

    @Test
    void elevenVersusElevenMatchesBruteForceOptimum() {
        var participants = randomParticipants(new Random(11L), 22, 85000, 125000);

        TeamAssignment result = assigner.assign(participants, 11);

        assertThat(result.teamAParticipantIds()).hasSize(11);
        assertThat(result.teamBParticipantIds()).hasSize(11);
        assertMatchesOracle(participants, 11, result);
    }

    @Test
    void elevenVersusElevenWithAllEqualMmrUsesFirstElevenIds() {
        var participants = sequentialParticipants(22);

        TeamAssignment result = assigner.assign(participants, 11);

        assertThat(result.teamAParticipantIds()).containsExactlyElementsOf(LongStream.rangeClosed(1, 11).boxed().toList());
        assertThat(result.teamBParticipantIds()).containsExactlyElementsOf(LongStream.rangeClosed(12, 22).boxed().toList());
        assertThat(result.captainAParticipantId()).isEqualTo(1L);
        assertThat(result.captainBParticipantId()).isEqualTo(12L);
    }

    @Test
    void matchesBruteForceOptimumForRandomInputs() {
        var random = new Random(42L);
        for (int round = 0; round < 300; round++) {
            int capacity = 1 + random.nextInt(7);
            // 절반은 좁은 범위로 만들어 동률 구성이 자주 생기게 한다.
            boolean narrow = round % 2 == 0;
            var participants = narrow
                    ? randomParticipants(random, capacity * 2, 99900, 100100)
                    : randomParticipants(random, capacity * 2, 80000, 130000);
            Collections.shuffle(participants, random);

            assertMatchesOracle(participants, capacity, assigner.assign(participants, capacity));
        }
    }

    /**
     * 결과가 올바른 분할이고, 전수 탐색으로 구한 최소 차이·사전순 첫 구성·팀장 규칙과 같은지 확인한다.
     * 탐색 순서와 무관하게 모든 A팀 비트마스크를 검사하는 독립 구현이다.
     */
    private void assertMatchesOracle(List<TeamCandidate> participants, int capacity, TeamAssignment result) {
        var sorted = participants.stream().sorted(Comparator.comparing(TeamCandidate::participantId)).toList();
        int n = sorted.size();
        long total = sorted.stream().mapToLong(TeamCandidate::mmrCents).sum();

        long bestDifference = Long.MAX_VALUE;
        int bestMask = 0;
        for (int mask = 1; mask < 1 << n; mask += 2) {
            if (Integer.bitCount(mask) != capacity) {
                continue;
            }
            long teamA = 0;
            for (int i = 0; i < n; i++) {
                if (((mask >>> i) & 1) == 1) {
                    teamA += sorted.get(i).mmrCents();
                }
            }
            long difference = Math.abs(2 * teamA - total);
            // 차이가 같으면 서로 다른 가장 작은 인덱스를 A팀에 가진 쪽이 사전순으로 앞선다.
            if (difference < bestDifference
                    || difference == bestDifference && (mask & Integer.lowestOneBit(mask ^ bestMask)) != 0) {
                bestDifference = difference;
                bestMask = mask;
            }
        }

        List<TeamCandidate> expectedA = new ArrayList<>();
        List<TeamCandidate> expectedB = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            (((bestMask >>> i) & 1) == 1 ? expectedA : expectedB).add(sorted.get(i));
        }
        assertThat(result.teamAParticipantIds()).containsExactlyElementsOf(ids(expectedA));
        assertThat(result.teamBParticipantIds()).containsExactlyElementsOf(ids(expectedB));
        assertThat(result.mmrDifference()).isEqualTo(BigDecimal.valueOf(bestDifference, 2));
        assertThat(result.teamAMmrSum().add(result.teamBMmrSum())).isEqualTo(BigDecimal.valueOf(total, 2));
        assertThat(result.teamAMmrSum().subtract(result.teamBMmrSum()).abs()).isEqualTo(result.mmrDifference());

        Set<Long> all = new HashSet<>(result.teamAParticipantIds());
        all.addAll(result.teamBParticipantIds());
        assertThat(all).hasSize(n);

        assertThat(result.captainAParticipantId()).isEqualTo(expectedCaptain(expectedA));
        assertThat(result.captainBParticipantId()).isEqualTo(expectedCaptain(expectedB));
    }

    private Long expectedCaptain(List<TeamCandidate> team) {
        BigDecimal highest = team.stream().map(TeamCandidate::mmr).max(Comparator.naturalOrder()).orElseThrow();
        return team.stream()
                .filter(member -> member.mmr().compareTo(highest) == 0)
                .mapToLong(TeamCandidate::participantId)
                .min()
                .orElseThrow();
    }

    private List<Long> ids(List<TeamCandidate> team) {
        return team.stream().map(TeamCandidate::participantId).toList();
    }

    /** ID 1부터 count까지, MMR은 모두 1000.00 */
    private List<TeamCandidate> sequentialParticipants(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> candidate(id, "1000")).toList();
    }

    /** 서로 다른 임의 ID와 [minCents, maxCents) 범위의 MMR */
    private List<TeamCandidate> randomParticipants(Random random, int count, int minCents, int maxCents) {
        Set<Long> usedIds = new HashSet<>();
        List<TeamCandidate> participants = new ArrayList<>(count);
        while (participants.size() < count) {
            long id = 1 + random.nextInt(10_000);
            if (usedIds.add(id)) {
                long cents = minCents + random.nextInt(maxCents - minCents);
                participants.add(new TeamCandidate(id, BigDecimal.valueOf(cents, 2)));
            }
        }
        return participants;
    }

    private TeamCandidate candidate(Long participantId, String mmr) {
        return new TeamCandidate(participantId, new BigDecimal(mmr));
    }
}
