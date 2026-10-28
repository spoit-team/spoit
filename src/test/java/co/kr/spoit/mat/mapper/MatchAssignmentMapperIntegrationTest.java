package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
class MatchAssignmentMapperIntegrationTest extends AbstractMySqlMapperTest {

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
        assertThat(match.getAssignmentConfirmedAt()).isEqualTo(LocalDateTime.of(2026, 10, 22, 18, 0));
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
}
