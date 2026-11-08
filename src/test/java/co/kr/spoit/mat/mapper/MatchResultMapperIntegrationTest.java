package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import co.kr.spoit.mat.domain.ReminderTarget;
import co.kr.spoit.mat.domain.ResultPageMatch;
import co.kr.spoit.mat.domain.ResultSubmission;
import co.kr.spoit.mat.domain.ResultTeamMember;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
class MatchResultMapperIntegrationTest extends AbstractMySqlMapperTest {

    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 10, 26, 12, 0);
    private static final LocalDateTime PROCESSED_AT = LocalDateTime.of(2026, 10, 26, 13, 0);

    @Autowired
    private MatchResultMapper resultMapper;

    @Autowired
    private SqlSessionTemplate sqlSession;

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
    void findsMatchInfoForResultPageEvenIfFacilityIsDeleted() {
        var match = resultMapper.findResultPageMatch(7L).orElseThrow();

        assertThat(match.getMatchId()).isEqualTo(7L);
        assertThat(match.getTitle()).isEqualTo("MAT 결과 제출 테스트 경기");
        assertThat(match.getStartAt()).isEqualTo(LocalDateTime.of(2026, 10, 26, 10, 0));
        assertThat(match.getEndAt()).isEqualTo(END_AT);
        assertThat(match.getStatus()).isEqualTo("매칭 완료");
        assertThat(match.getFacilityName()).isEqualTo("MAT 테스트 시설");
        assertThat(match.getSportName()).isEqualTo("MAT 테스트 종목 1");
        assertThat(match.getCaptainAParticipantId()).isEqualTo(70L);
        assertThat(match.getCaptainBParticipantId()).isEqualTo(71L);
        assertThat(resultMapper.findResultPageMatch(4L)).map(ResultPageMatch::getFacilityName)
                .contains("MAT 삭제된 테스트 시설");
        assertThat(resultMapper.findResultPageMatch(999L)).isEmpty();
    }

    @Test
    void findsOnlyConfirmedTeamMembersWithNicknames() {
        // 72(회원 5)는 같은 경기의 취소 참가자라 빠진다.
        assertThat(resultMapper.findConfirmedTeamMembers(7L, "확정"))
                .extracting(ResultTeamMember::getParticipantId, ResultTeamMember::getUserId,
                        ResultTeamMember::getNickname, ResultTeamMember::getMatchSide)
                .containsExactly(tuple(70L, 3L, "MAT 테스트 회원 3", "A"),
                        tuple(71L, 4L, "MAT 테스트 회원 4", "B"));
        assertThat(resultMapper.findConfirmedTeamMembers(999L, "확정")).isEmpty();
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
    void findsCaptainsWithoutResultOrEvaluationInReminderWindow() {
        // 팀장 70(회원 3)은 결과는 냈지만 평가가 없고, 71(회원 4)은 둘 다 없다. 구간은 시작을 빼고 끝을 넣는다.
        assertThat(reminderTargets(END_AT.minusMinutes(1), END_AT))
                .extracting(ReminderTarget::getMatchId, ReminderTarget::getUserId)
                .containsExactly(tuple(7L, 3L), tuple(7L, 4L));
        assertThat(reminderTargets(END_AT, END_AT.plusMinutes(1))).isEmpty();
        // 6은 같은 구간에 끝났지만 팀장이 없다.
        LocalDateTime match6EndAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        assertThat(reminderTargets(match6EndAt.minusMinutes(1), match6EndAt)).isEmpty();

        // 결과와 평가를 모두 낸 70은 빠지고, 평가만 낸 71은 결과가 없어 남는다.
        insertEvaluation(70L, 71L);
        insertEvaluation(71L, 70L);
        sqlSession.clearCache();
        assertThat(reminderTargets(END_AT.minusMinutes(1), END_AT))
                .extracting(ReminderTarget::getMatchId, ReminderTarget::getUserId)
                .containsExactly(tuple(7L, 4L));
    }

    @Test
    void findsResultSubmittableMatchesOnlyForConfirmedCaptainsWithinPeriod() {
        LocalDateTime now = END_AT.plusHours(1);
        LocalDateTime deadline = END_AT.plusHours(24);
        // 경기 7의 두 팀장이 대상이다. 같은 회원들이 확정 참가한 경기 6은 팀장이 없어 빠진다.
        assertThat(submittableMatchIds(3L, now)).containsExactly(7L);
        assertThat(submittableMatchIds(4L, now)).containsExactly(7L);
        // 제출 기간은 종료 시각을 넣고 마감 시각을 뺀다.
        assertThat(submittableMatchIds(3L, END_AT)).containsExactly(7L);
        assertThat(submittableMatchIds(3L, END_AT.minusSeconds(1))).isEmpty();
        assertThat(submittableMatchIds(3L, deadline.minusSeconds(1))).containsExactly(7L);
        assertThat(submittableMatchIds(3L, deadline)).isEmpty();

        // A팀장 자리를 취소 참가자 72(회원 5)로 바꾸면 회원 3은 팀원으로 남아 빠지고, 회원 5도 확정 참가가 아니라 빠진다.
        updateMatch7("captain_a_participant_id = 72");
        assertThat(submittableMatchIds(3L, now)).isEmpty();
        assertThat(submittableMatchIds(5L, now)).isEmpty();
        assertThat(submittableMatchIds(4L, now)).containsExactly(7L);

        // 매칭 완료가 아니거나 레이팅 처리가 끝난 경기는 빠진다.
        updateMatch7("status = '종료'");
        assertThat(submittableMatchIds(4L, now)).isEmpty();
        updateMatch7("status = '매칭 완료', rating_status = 'APPLIED'");
        assertThat(submittableMatchIds(4L, now)).isEmpty();
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

    private List<Long> submittableMatchIds(Long userId, LocalDateTime now) {
        return resultMapper.findResultSubmittableMatchIds(userId, "매칭 완료", "WAITING", "확정",
                now.minusHours(24), now);
    }

    private void insertEvaluation(Long evaluatorId, Long targetId) {
        jdbcTemplate.update("INSERT INTO match_evaluation (match_id, sport_id, evaluator_participant_id,"
                + " target_participant_id, overall_score, status, submitted_at)"
                + " VALUES (7, 1, ?, ?, NULL, '제출 완료', '2026-10-26 12:30:00')", evaluatorId, targetId);
    }

    // jdbcTemplate으로 바꾼 값은 MyBatis 1차 캐시가 모르므로, 같은 인자로 다시 조회하기 전에 캐시를 비운다.
    private void updateMatch7(String assignments) {
        jdbcTemplate.update("UPDATE social_match SET " + assignments + " WHERE match_id = 7");
        sqlSession.clearCache();
    }

    private String statusOf(Long matchId) {
        return jdbcTemplate.queryForObject("SELECT status FROM social_match WHERE match_id = ?", String.class, matchId);
    }
}
