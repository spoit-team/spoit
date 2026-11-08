package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.RatingPolicy;
import co.kr.spoit.mat.domain.RatingStatus;
import co.kr.spoit.mat.mapper.MatchResultMapper;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 지난 참여 카드에 결과 제출 버튼을 붙일 경기를 찾는다.
 *
 * <p>결과 화면 자체는 {@link MatchResultService#getSummary}가 담당하므로 화면 모델을 여기서 만들지 않는다.</p>
 */
@Service
public class MatchResultPageService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MatchResultMapper resultMapper;

    public MatchResultPageService(MatchResultMapper resultMapper) {
        this.resultMapper = resultMapper;
    }

    /** 마이페이지 지난 참여 카드에 결과 제출 버튼을 붙일 경기. 비로그인이면 빈 Set */
    @Transactional(readOnly = true)
    public Set<Long> findResultSubmittableMatchIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        return new HashSet<>(resultMapper.findResultSubmittableMatchIds(userId, MatchStatus.MATCHED.dbValue(),
                RatingStatus.WAITING.name(), ParticipationStatus.CONFIRMED.dbValue(),
                now.minus(RatingPolicy.RESULT_SUBMIT_PERIOD), now));
    }
}
