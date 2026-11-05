package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.AssignmentMatch;
import co.kr.spoit.mat.domain.RatingStatus;
import co.kr.spoit.mat.domain.SportRating;
import co.kr.spoit.mat.dto.ParticipantHistoryView;
import co.kr.spoit.mat.dto.SportRatingResponse;
import co.kr.spoit.mat.mapper.MatchAssignmentMapper;
import co.kr.spoit.mat.mapper.ParticipantHistoryMapper;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영 화면의 참가자 이력 모달. 담당 시설 확인은 팀 배정 화면과 같은 방식으로 한다. */
@Service
public class ParticipantHistoryService {

    // 신고·제재는 시스템 관리 담당 도메인이라 enum이 없어 db/seed.sql의 report.status·penalty.status 값을 쓴다.
    private static final String REPORT_CONFIRMED = "처리 완료";
    private static final String PENALTY_ACTIVE = "적용 중";
    private static final String NO_RATING_LABEL = "기록 없음";
    private static final int RECENT_CHANGE_LIMIT = 5;

    private final MatchAssignmentMapper assignmentMapper;
    private final ParticipantHistoryMapper historyMapper;
    private final SportRatingMapper ratingMapper;

    public ParticipantHistoryService(MatchAssignmentMapper assignmentMapper,
                                     ParticipantHistoryMapper historyMapper,
                                     SportRatingMapper ratingMapper) {
        this.assignmentMapper = assignmentMapper;
        this.historyMapper = historyMapper;
        this.ratingMapper = ratingMapper;
    }

    /** 경기가 없거나 그 경기에 신청·참가 기록이 없는 회원이면 empty */
    @Transactional(readOnly = true)
    public Optional<ParticipantHistoryView> getHistory(long matchId, long userId, long managerUserId,
                                                       LocalDateTime now) {
        AssignmentMatch match = assignmentMapper.findAssignmentMatch(matchId).orElse(null);
        if (match == null) {
            return Optional.empty();
        }
        if (!assignmentMapper.isFacilityManager(match.getFacilityId(), managerUserId)) {
            throw new AccessDeniedException("담당 시설의 매칭방이 아닙니다.");
        }
        // 담당 경기의 번호로 아무 회원이나 조회하지 못하게, 그 경기에 신청·참가 기록이 있는 회원만 보여 준다.
        String nickname = historyMapper.findNicknameInMatch(matchId, userId).orElse(null);
        if (nickname == null) {
            return Optional.empty();
        }

        SportRating rating = ratingMapper.findByUserIdAndSportId(userId, match.getSportId()).orElse(null);
        return Optional.of(new ParticipantHistoryView(nickname,
                rating == null ? NO_RATING_LABEL : SportRatingResponse.from(rating).tierLabel(),
                rating == null ? 0 : rating.getRatedMatchCount(),
                historyMapper.findRecentChanges(userId, match.getSportId(), RECENT_CHANGE_LIMIT).stream()
                        .map(change -> new ParticipantHistoryView.RecentChange(change.getCalculatedAt(),
                                change.getFinalDelta()))
                        .toList(),
                historyMapper.countExcludedMatches(userId, ParticipationStatus.CONFIRMED.dbValue(),
                        RatingStatus.EXCLUDED.name(), MatchStatus.MATCHED.dbValue(), MatchStatus.CLOSED.dbValue()),
                historyMapper.countReports(userId, REPORT_CONFIRMED),
                historyMapper.findActivePenalties(userId, PENALTY_ACTIVE, now).stream()
                        .map(penalty -> new ParticipantHistoryView.Penalty(penalty.getPenaltyType(),
                                penalty.getEndAt()))
                        .toList()));
    }
}
