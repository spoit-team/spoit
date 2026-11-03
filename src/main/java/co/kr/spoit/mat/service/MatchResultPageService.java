package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.RatingPolicy;
import co.kr.spoit.mat.domain.ResultPageMatch;
import co.kr.spoit.mat.domain.ResultTeamMember;
import co.kr.spoit.mat.dto.MatchResultPage;
import co.kr.spoit.mat.mapper.MatchResultMapper;
import co.kr.spoit.match.domain.ParticipationStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경기 결과 제출 화면에 넣을 경기 정보와 양 팀 명단. 제출 상태는 화면 스크립트가 결과 조회 API로 받는다.
 *
 * <p>팀장이 아니면 경기 정보와 명단을 넘기지 않는다. 로그인한 누구나 경기 번호만 바꿔 다른 경기의
 * 참가자를 볼 수 있게 되기 때문이다.</p>
 */
@Service
public class MatchResultPageService {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E) HH:mm", Locale.KOREAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String SIDE_A = "A";
    private static final String SIDE_B = "B";

    private final MatchResultMapper resultMapper;

    public MatchResultPageService(MatchResultMapper resultMapper) {
        this.resultMapper = resultMapper;
    }

    /** 경기가 없으면 empty */
    @Transactional(readOnly = true)
    public Optional<MatchResultPage> getPage(long matchId, long userId, LocalDateTime now) {
        ResultPageMatch match = resultMapper.findResultPageMatch(matchId).orElse(null);
        if (match == null) {
            return Optional.empty();
        }
        List<ResultTeamMember> members = resultMapper.findConfirmedTeamMembers(matchId,
                ParticipationStatus.CONFIRMED.dbValue());
        String mySide = captainSideOf(match, members, userId);
        if (mySide == null) {
            return Optional.of(MatchResultPage.notCaptain(matchId));
        }
        LocalDateTime deadline = match.getEndAt().plus(RatingPolicy.RESULT_SUBMIT_PERIOD);
        return Optional.of(new MatchResultPage(matchId, true, match.getTitle(), match.getSportName(),
                match.getFacilityName(), scheduleOf(match), mySide, deadline.format(DATE_TIME),
                closedNoteOf(match, deadline, now),
                List.of(teamOf(SIDE_A, match.getCaptainAParticipantId(), mySide, members),
                        teamOf(SIDE_B, match.getCaptainBParticipantId(), mySide, members))));
    }

    // 결과 API와 같이 확정 참가 중인 팀장만 인정하고, 팀은 팀장 자리로 정한다.
    private static String captainSideOf(ResultPageMatch match, List<ResultTeamMember> members, long userId) {
        for (ResultTeamMember member : members) {
            if (!Objects.equals(member.getUserId(), userId)) {
                continue;
            }
            if (Objects.equals(member.getParticipantId(), match.getCaptainAParticipantId())) {
                return SIDE_A;
            }
            if (Objects.equals(member.getParticipantId(), match.getCaptainBParticipantId())) {
                return SIDE_B;
            }
        }
        return null;
    }

    private static MatchResultPage.Team teamOf(String side, Long captainId, String mySide,
                                               List<ResultTeamMember> members) {
        List<MatchResultPage.Member> team = members.stream()
                .filter(member -> side.equals(member.getMatchSide()))
                .map(member -> new MatchResultPage.Member(member.getNickname(),
                        member.getParticipantId().equals(captainId)))
                .sorted(Comparator.comparing(MatchResultPage.Member::isCaptain).reversed())
                .toList();
        return new MatchResultPage.Team(side, side.equals(mySide), team);
    }

    private static String scheduleOf(ResultPageMatch match) {
        LocalDateTime start = match.getStartAt();
        LocalDateTime end = match.getEndAt();
        String endText = end.toLocalDate().equals(start.toLocalDate()) ? end.format(TIME) : end.format(DATE_TIME);
        return start.format(DATE_TIME) + " ~ " + endText;
    }

    // 조회 API의 canSubmit만으로는 이유를 알 수 없을 때 보여 줄 안내. 판단 순서는 API의 제출 검사와 같다.
    private static String closedNoteOf(ResultPageMatch match, LocalDateTime deadline, LocalDateTime now) {
        if (!MatchResultService.PLAYED_STATUSES.contains(match.getStatus())) {
            return "결과를 제출할 수 있는 경기가 아닙니다.";
        }
        if (now.isBefore(match.getEndAt())) {
            return "경기가 끝난 뒤 결과를 제출할 수 있습니다. 종료 시각은 " + match.getEndAt().format(DATE_TIME) + "입니다.";
        }
        if (!now.isBefore(deadline)) {
            return "제출 마감 시각이 지나 결과를 제출할 수 없습니다.";
        }
        return "지금은 결과를 제출할 수 없습니다.";
    }
}
