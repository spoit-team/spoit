package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import co.kr.spoit.mat.domain.ReminderTarget;
import co.kr.spoit.mat.domain.ResultSubmission;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
class MatchResultMapperIntegrationTest extends AbstractMySqlMapperTest {

    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 10, 26, 12, 0);
    private static final LocalDateTime PROCESSED_AT = LocalDateTime.of(2026, 10, 26, 13, 0);

    @Autowired
    private MatchResultMapper resultMapper;

    @BeforeEach
    void assignCaptains() {
        // 팀장 FK가 참가자 행을 가리켜 fixture INSERT로는 지정할 수 없다.
        jdbcTemplate.update("UPDATE social_match SET captain_a_participant_id = 70, captain_b_participant_id = 71"
                + " WHERE match_id = 7");
    }

    @Test
    void locksResultFieldsAndIgnoresFacilityDeletion() {
        var match = resultMapper.lockResultMatch(7L).orElseThrow();

        assertThat(match.getMatchId()).isEqualTo(7L);
        assertThat(match.getFacilityId()).isEqualTo(1L);
        assertThat(match.getSportId()).isEqualTo(1L);
        assertThat(match.getStartAt()).isEqualTo(LocalDateTime.of(2026, 10, 26, 10, 0));
        assertThat(match.getEndAt()).isEqualTo(END_AT);
        assertThat(match.getStatus()).isEqualTo("매칭 완료");
        assertThat(match.getCaptainAParticipantId()).isEqualTo(70L);
        assertThat(match.getCaptainBParticipantId()).isEqualTo(71L);
        assertThat(match.getResultStatus()).isNull();
        assertThat(match.getFinalResult()).isNull();
        assertThat(match.getRatingStatus()).isEqualTo("WAITING");
        // 4는 삭제된 시설의 경기다. 종료 뒤 시설이 삭제돼도 결과는 처리해야 한다.
        assertThat(resultMapper.findResultMatch(4L)).isPresent();
        assertThat(resultMapper.lockResultMatch(999L)).isEmpty();
    }

    @Test
    void keepsWaitingMatchesMatchedAfterEndTime() {
        // 3·6·7은 종료 시각이 지난 매칭 완료지만 모두 WAITING이라 아직 결과 처리 중이다.
        assertThat(endedMatchIds(END_AT)).isEmpty();
        assertThat(markEnded(6L, END_AT)).isZero();
        assertThat(markEnded(7L, END_AT)).isZero();

        assertThat(statusOf(6L)).isEqualTo("매칭 완료");
        assertThat(statusOf(7L)).isEqualTo("매칭 완료");
    }

    @Test
    void endsMatchedMatchesWhoseRatingIsProcessedAfterEndTime() {
        jdbcTemplate.update("UPDATE social_match SET rating_status = 'APPLIED' WHERE match_id = 3");
        jdbcTemplate.update("UPDATE social_match SET rating_status = 'EXCLUDED' WHERE match_id = 7");

        // 6은 WAITING이라 남는다. 7은 종료 시각과 같은 경계값이고, 취소 8은 EXCLUDED여도 대상이 아니다.
        assertThat(endedMatchIds(END_AT)).containsExactly(3L, 7L);
        assertThat(markEnded(6L, END_AT)).isZero();
        assertThat(markEnded(8L, END_AT)).isZero();
        assertThat(markEnded(7L, END_AT)).isEqualTo(1);
        assertThat(markEnded(3L, END_AT)).isEqualTo(1);

        assertThat(statusOf(3L)).isEqualTo("종료");
        assertThat(statusOf(6L)).isEqualTo("매칭 완료");
        assertThat(statusOf(7L)).isEqualTo("종료");
        assertThat(statusOf(8L)).isEqualTo("취소");
    }

    @Test
    void keepsMatchExcludedDuringPlayMatchedUntilEndTime() {
        // 경기 7(10:00~12:00)을 경기 중에 관리자가 레이팅에서 제외했다.
        assertThat(resultMapper.markRatingProcessed(7L, "EXCLUDED", END_AT.minusHours(1), 1L, "WAITING"))
                .isEqualTo(1);

        assertThat(endedMatchIds(END_AT.minusSeconds(1))).doesNotContain(7L);
        assertThat(markEnded(7L, END_AT.minusSeconds(1))).isZero();
        assertThat(statusOf(7L)).isEqualTo("매칭 완료");

        assertThat(endedMatchIds(END_AT)).contains(7L);
        assertThat(markEnded(7L, END_AT)).isEqualTo(1);
        assertThat(statusOf(7L)).isEqualTo("종료");
    }

    @Test
    void findsWaitingMatchesWhoseSubmissionPeriodHasEnded() {
        // 취소 8은 이미 EXCLUDED라 가장 먼저 끝났어도 빠진다. 6은 종료 시각과 같은 경계값이다.
        LocalDateTime endedBefore = LocalDateTime.of(2026, 10, 25, 12, 0);

        assertThat(resultMapper.findExpiredMatchIds("WAITING", endedBefore))
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(resultMapper.findExpiredMatchIds("WAITING", endedBefore.minusSeconds(1)))
                .containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void savesSubmissionsAndReadsOnlyActiveRows() {
        assertThat(resultMapper.findActiveSubmissions(7L, "REPLACED"))
                .extracting(ResultSubmission::getSubmissionId, ResultSubmission::getCaptainParticipantId,
                        ResultSubmission::getReportedResult, ResultSubmission::getSubmissionStatus,
                        ResultSubmission::getSubmittedAt)
                .containsExactly(tuple(701L, 70L, "B_WIN", "SUBMITTED", LocalDateTime.of(2026, 10, 26, 12, 20)));

        resultMapper.insertSubmission(7L, 71L, "B_WIN", "CONFIRMED", PROCESSED_AT);
        assertThat(resultMapper.updateSubmissionStatus(701L, "CONFIRMED")).isEqualTo(1);

        assertThat(resultMapper.findActiveSubmissions(7L, "REPLACED"))
                .extracting(ResultSubmission::getCaptainParticipantId, ResultSubmission::getReportedResult,
                        ResultSubmission::getSubmissionStatus, ResultSubmission::getSubmittedAt)
                .containsExactly(tuple(70L, "B_WIN", "CONFIRMED", LocalDateTime.of(2026, 10, 26, 12, 20)),
                        tuple(71L, "B_WIN", "CONFIRMED", PROCESSED_AT));
    }

    @Test
    void savesResultAndChangesRatingStatusOnlyFromExpectedState() {
        assertThat(resultMapper.markResult(7L, "CONFIRMED", "A_WIN", PROCESSED_AT)).isEqualTo(1);
        assertThat(resultMapper.markRatingProcessed(7L, "EXCLUDED", PROCESSED_AT, 1L, "WAITING")).isEqualTo(1);
        assertThat(resultMapper.markRatingProcessed(7L, "APPLIED", PROCESSED_AT, null, "WAITING")).isZero();

        var match = resultMapper.findResultMatch(7L).orElseThrow();
        assertThat(match.getResultStatus()).isEqualTo("CONFIRMED");
        assertThat(match.getFinalResult()).isEqualTo("A_WIN");
        assertThat(match.getRatingStatus()).isEqualTo("EXCLUDED");
        var row = jdbcTemplate.queryForMap("SELECT result_confirmed_at, rating_processed_at, result_reviewed_by"
                + " FROM social_match WHERE match_id = 7");
        assertThat(row.get("result_confirmed_at")).isEqualTo(PROCESSED_AT);
        assertThat(row.get("rating_processed_at")).isEqualTo(PROCESSED_AT);
        assertThat(row.get("result_reviewed_by")).isEqualTo(1L);

        // 자동 반영처럼 검토자가 없는 처리도 저장된다.
        assertThat(resultMapper.markRatingProcessed(6L, "APPLIED", PROCESSED_AT, null, "WAITING")).isEqualTo(1);
        assertThat(resultMapper.findResultMatch(6L).orElseThrow().getRatingStatus()).isEqualTo("APPLIED");
        assertThat(jdbcTemplate.queryForObject("SELECT result_reviewed_by FROM social_match WHERE match_id = 6",
                Long.class)).isNull();
    }

    @Test
    void findsCaptainsWithoutSubmissionInReminderWindow() {
        // 팀장 70은 제출 행이 있어 빠지고 71(회원 4)만 대상이다. 구간은 시작을 빼고 끝을 넣는다.
        assertThat(reminderTargets(END_AT.minusMinutes(1), END_AT))
                .extracting(ReminderTarget::getMatchId, ReminderTarget::getUserId)
                .containsExactly(tuple(7L, 4L));
        assertThat(reminderTargets(END_AT, END_AT.plusMinutes(1))).isEmpty();
        // 6은 같은 구간에 끝났지만 팀장이 없다.
        LocalDateTime match6EndAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        assertThat(reminderTargets(match6EndAt.minusMinutes(1), match6EndAt)).isEmpty();
    }

    private List<Long> endedMatchIds(LocalDateTime now) {
        return resultMapper.findEndedMatchIds("매칭 완료", "APPLIED", "EXCLUDED", now);
    }

    private int markEnded(Long matchId, LocalDateTime now) {
        return resultMapper.markEnded(matchId, "종료", "매칭 완료", "APPLIED", "EXCLUDED", now);
    }

    private List<ReminderTarget> reminderTargets(LocalDateTime endedAfter, LocalDateTime endedUntil) {
        return resultMapper.findReminderTargets("매칭 완료", "종료", "WAITING", "확정", endedAfter, endedUntil);
    }

    private String statusOf(Long matchId) {
        return jdbcTemplate.queryForObject("SELECT status FROM social_match WHERE match_id = ?", String.class, matchId);
    }
}
