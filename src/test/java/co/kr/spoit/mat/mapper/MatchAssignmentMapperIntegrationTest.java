package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import co.kr.spoit.mat.domain.AssignmentMember;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
class MatchAssignmentMapperIntegrationTest extends AbstractMySqlMapperTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 23, 18, 0);

    @Autowired
    private MatchAssignmentMapper assignmentMapper;

    @Test
    void mapsAssignmentFields() {
        var match = assignmentMapper.findAssignmentMatch(3L).orElseThrow();

        assertThat(match.getMatchId()).isEqualTo(3L);
        assertThat(match.getFacilityId()).isEqualTo(1L);
        assertThat(match.getStartAt()).isEqualTo(LocalDateTime.of(2026, 10, 23, 10, 0));
        assertThat(match.getStatus()).isEqualTo("매칭 완료");
        assertThat(match.getTeamACapacity()).isEqualTo(3);
        assertThat(match.getTeamBCapacity()).isEqualTo(3);
        assertThat(match.getAssignmentMethod()).isEqualTo("MANUAL");
        assertThat(match.getAssignmentConfirmedAt()).isEqualTo(LocalDateTime.of(2026, 10, 22, 18, 0));
        assertThat(match.getCaptainAParticipantId()).isNull();
        assertThat(match.getCaptainBParticipantId()).isNull();
    }

    @Test
    void excludesMatchOfDeletedFacilityAndMissingMatch() {
        assertThat(assignmentMapper.findAssignmentMatch(4L)).isEmpty();
        assertThat(assignmentMapper.findAssignmentMatch(999L)).isEmpty();
    }

    @Test
    void checksManagerByFacilityAndUser() {
        assertThat(assignmentMapper.isFacilityManager(1L, 1L)).isTrue();
        assertThat(assignmentMapper.isFacilityManager(1L, 2L)).isFalse();
        assertThat(assignmentMapper.isFacilityManager(2L, 1L)).isFalse();
    }

    @Test
    void locksMatchWithSameFieldsAndFacilityFilter() {
        var match = assignmentMapper.lockAssignmentMatch(5L).orElseThrow();

        assertThat(match.getMatchId()).isEqualTo(5L);
        assertThat(match.getStatus()).isEqualTo("모집 중");
        assertThat(match.getTeamACapacity()).isEqualTo(1);
        assertThat(match.getAssignmentConfirmedAt()).isNull();
        assertThat(assignmentMapper.lockAssignmentMatch(4L)).isEmpty();
    }

    @Test
    void savesAssignmentOnlyForRecruitingMatch() {
        assertThat(assignmentMapper.updateMatchSide(5L, List.of(50L), "A", "확정")).isEqualTo(1);
        assertThat(assignmentMapper.updateMatchSide(5L, List.of(51L), "B", "확정")).isEqualTo(1);
        assertThat(assignmentMapper.markAssigned(5L, "AUTO", 1L, CONFIRMED_AT, 50L, 51L,
                "매칭 완료", "모집 중")).isEqualTo(1);

        var match = assignmentMapper.findAssignmentMatch(5L).orElseThrow();
        assertThat(match.getStatus()).isEqualTo("매칭 완료");
        assertThat(match.getAssignmentMethod()).isEqualTo("AUTO");
        assertThat(match.getAssignmentConfirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(match.getCaptainAParticipantId()).isEqualTo(50L);
        assertThat(match.getCaptainBParticipantId()).isEqualTo(51L);
        assertThat(jdbcTemplate.queryForObject("SELECT assigned_by FROM social_match WHERE match_id = 5", Long.class))
                .isEqualTo(1L);
        assertThat(assignmentMapper.findConfirmedMembers(5L, "확정"))
                .extracting(AssignmentMember::getParticipantId, AssignmentMember::getNickname,
                        AssignmentMember::getMatchSide)
                .containsExactly(tuple(50L, "MAT 테스트 회원 1", "A"), tuple(51L, "MAT 테스트 회원 2", "B"));

        // 이미 매칭 완료라 다시 저장되지 않는다.
        assertThat(assignmentMapper.markAssigned(5L, "MANUAL", 1L, CONFIRMED_AT, 51L, 50L,
                "매칭 완료", "모집 중")).isZero();
    }

    @Test
    void resetsAssignmentToRecruitingDefaults() {
        assignmentMapper.updateMatchSide(5L, List.of(50L), "A", "확정");
        assignmentMapper.updateMatchSide(5L, List.of(51L), "B", "확정");
        assignmentMapper.markAssigned(5L, "AUTO", 1L, CONFIRMED_AT, 50L, 51L, "매칭 완료", "모집 중");

        assertThat(assignmentMapper.clearMatchSides(5L, "확정")).isEqualTo(2);
        assertThat(assignmentMapper.clearAssignment(5L, "MANUAL", "모집 중", "매칭 완료")).isEqualTo(1);

        var match = assignmentMapper.findAssignmentMatch(5L).orElseThrow();
        assertThat(match.getStatus()).isEqualTo("모집 중");
        assertThat(match.getAssignmentMethod()).isEqualTo("MANUAL");
        assertThat(match.getAssignmentConfirmedAt()).isNull();
        assertThat(match.getCaptainAParticipantId()).isNull();
        assertThat(match.getCaptainBParticipantId()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT assigned_by FROM social_match WHERE match_id = 5", Long.class))
                .isNull();
        assertThat(assignmentMapper.findConfirmedMembers(5L, "확정"))
                .extracting(AssignmentMember::getMatchSide)
                .containsOnlyNulls();
        // 모집 중인 경기는 취소 대상이 아니다.
        assertThat(assignmentMapper.clearAssignment(5L, "MANUAL", "모집 중", "매칭 완료")).isZero();
    }
}
