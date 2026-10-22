package co.kr.spoit.mat.service;

import co.kr.spoit.mat.dto.MatchParticipantResponse;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 참가자 조회 진입점. 참가 승인·취소와 A/B 배정은 별도 구현 대상이다. */
@Service
@Transactional(readOnly = true)
public class MatchParticipantService {

    private final MatchParticipantMapper participantMapper;

    public MatchParticipantService(MatchParticipantMapper participantMapper) {
        this.participantMapper = participantMapper;
    }

    /**
     * 모든 상태의 참가자를 ID 순으로 반환한다.
     * 빈 목록은 해당 경기의 참가자 행이 없음을 뜻하며, 경기 존재 여부를 보장하지 않는다.
     */
    public List<MatchParticipantResponse> findByMatchId(Long matchId) {
        if (matchId == null || matchId <= 0) {
            throw new IllegalArgumentException("matchId는 양수여야 합니다.");
        }
        return participantMapper.findByMatchId(matchId).stream()
                .map(MatchParticipantResponse::from)
                .toList();
    }
}
