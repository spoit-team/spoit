package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import co.kr.spoit.mat.domain.ActivePenalty;
import co.kr.spoit.mat.domain.RecentRatingChange;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
class ParticipantHistoryMapperIntegrationTest extends AbstractMySqlMapperTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 0, 0);

    @Autowired
    private ParticipantHistoryMapper historyMapper;

    @Autowired
    private SqlSessionTemplate sqlSession;

    @Test
    void findsNicknameOnlyForApplicantsOrParticipantsOfMatch() {
        // 경기 6: 회원 3은 확정 참가, 회원 5는 대기 신청만 있다. 경기 7의 회원 5는 신청·참가 모두 취소다.
        assertThat(historyMapper.findNicknameInMatch(6L, 3L)).contains("MAT 테스트 회원 3");
        assertThat(historyMapper.findNicknameInMatch(6L, 5L)).contains("MAT 테스트 회원 5");
        assertThat(historyMapper.findNicknameInMatch(7L, 5L)).contains("MAT 테스트 회원 5");
        assertThat(historyMapper.findNicknameInMatch(6L, 1L)).isEmpty();
        assertThat(historyMapper.findNicknameInMatch(999L, 3L)).isEmpty();

        // 동호회 신청 팀원처럼 회원 1이 본인 신청 행 없이 회원 3의 신청(60)에 참가자로만 들어온다.
        jdbcTemplate.update("INSERT INTO match_participant (participant_id, application_id, match_id, user_id,"
                + " participation_status, match_side, entry_mmr, joined_at)"
                + " VALUES (64, 60, 6, 1, '확정', NULL, 1000.00, '2026-10-20 09:00:00')");
        sqlSession.clearCache();
        assertThat(historyMapper.findNicknameInMatch(6L, 1L)).contains("MAT 테스트 회원 1");
    }

    @Test
    void findsLatestRatingChangesOfSportFirst() {
        insertHistory(6L, 60L, 3L, 1L, "7.69", "2026-10-25 13:00:00");
        insertHistory(7L, 70L, 3L, 1L, "-3.10", "2026-10-26 13:00:00");
        insertHistory(6L, 61L, 4L, 1L, "-15.38", "2026-10-25 13:00:00");
        insertHistory(2L, 5L, 2L, 2L, "5.00", "2026-10-22 13:00:00");
        insertHistory(1L, 20L, 2L, 1L, "1.00", "2026-10-22 13:00:00");

        assertThat(historyMapper.findRecentChanges(3L, 1L, 5))
                .extracting(RecentRatingChange::getCalculatedAt, RecentRatingChange::getFinalDelta)
                .containsExactly(tuple(LocalDateTime.of(2026, 10, 26, 13, 0), new BigDecimal("-3.10")),
                        tuple(LocalDateTime.of(2026, 10, 25, 13, 0), new BigDecimal("7.69")));
        assertThat(historyMapper.findRecentChanges(3L, 1L, 1))
                .extracting(RecentRatingChange::getFinalDelta).containsExactly(new BigDecimal("-3.10"));
        // 회원 2의 종목 2 이력은 빠진다.
        assertThat(historyMapper.findRecentChanges(2L, 1L, 5))
                .extracting(RecentRatingChange::getFinalDelta).containsExactly(new BigDecimal("1.00"));
        assertThat(historyMapper.findRecentChanges(5L, 1L, 5)).isEmpty();
    }

    @Test
    void countsExcludedMatchesPlayedAsConfirmedParticipant() {
        assertThat(excludedMatches(3L)).isZero();

        jdbcTemplate.update("UPDATE social_match SET rating_status = 'EXCLUDED' WHERE match_id = 6");
        jdbcTemplate.update("UPDATE social_match SET status = '종료', rating_status = 'EXCLUDED' WHERE match_id = 7");
        // 취소 경기 8에도 회원 3을 확정 참가자로 넣는다. 자동 취소 경기는 EXCLUDED여도 세지 않는다.
        jdbcTemplate.update("INSERT INTO match_application (application_id, match_id, applicant_user_id,"
                + " application_type, status, receipt_no, created_at, confirmed_at)"
                + " VALUES (80, 8, 3, '개인', '확정', 'MAT-TEST-RECEIPT-80', '2026-10-19 09:00:00',"
                + " '2026-10-19 09:00:00')");
        jdbcTemplate.update("INSERT INTO match_participant (participant_id, application_id, match_id, user_id,"
                + " participation_status, match_side, entry_mmr, joined_at)"
                + " VALUES (80, 80, 8, 3, '확정', 'A', 1000.00, '2026-10-19 09:00:00')");
        sqlSession.clearCache();

        assertThat(excludedMatches(3L)).isEqualTo(2);
        assertThat(excludedMatches(4L)).isEqualTo(2);
        // 회원 6은 경기 6의 취소 참가자라 세지 않는다.
        assertThat(excludedMatches(6L)).isZero();
    }

    @Test
    void countsOnlyConfirmedReportsAgainstMember() {
        // 회원 3의 신고 3건 중 기각·접수는 세지 않는다.
        assertThat(historyMapper.countReports(3L, "처리 완료")).isEqualTo(1);
        assertThat(historyMapper.countReports(4L, "처리 완료")).isEqualTo(1);
        assertThat(historyMapper.countReports(5L, "처리 완료")).isZero();
    }

    @Test
    void findsPenaltiesStillInEffectLatestFirst() {
        // 종료 시각이 지난 적용 중 제재(3)와 해제된 제재(4)는 빠진다.
        assertThat(historyMapper.findActivePenalties(3L, "적용 중", NOW))
                .extracting(ActivePenalty::getPenaltyType, ActivePenalty::getEndAt)
                .containsExactly(tuple("이용 제한", LocalDateTime.of(2026, 10, 31, 10, 0)), tuple("경고", null));
        // 종료 시각과 같은 시각에는 이미 끝난 제재다.
        assertThat(historyMapper.findActivePenalties(3L, "적용 중", LocalDateTime.of(2026, 10, 31, 10, 0)))
                .extracting(ActivePenalty::getPenaltyType).containsExactly("경고");
        assertThat(historyMapper.findActivePenalties(5L, "적용 중", NOW)).isEmpty();
    }

    private int excludedMatches(Long userId) {
        return historyMapper.countExcludedMatches(userId, "확정", "EXCLUDED", "매칭 완료", "종료");
    }

    private void insertHistory(Long matchId, Long participantId, Long userId, Long sportId, String finalDelta,
                               String calculatedAt) {
        BigDecimal delta = new BigDecimal(finalDelta);
        jdbcTemplate.update("INSERT INTO sport_rating_history (match_id, participant_id, user_id, sport_id,"
                        + " mmr_before, mmr_after, elo_delta, evaluation_delta, final_delta, evaluation_score,"
                        + " actual_score, expected_score, team_avg_mmr_a, team_avg_mmr_b, algorithm_version,"
                        + " calculated_at) VALUES (?, ?, ?, ?, 1000.00, ?, ?, 0, ?, NULL, 0.5, 0.5, 1000.00, 1000.00,"
                        + " 'ELO-K32-V1', ?)",
                matchId, participantId, userId, sportId, new BigDecimal("1000.00").add(delta), delta, delta,
                calculatedAt);
    }
}
