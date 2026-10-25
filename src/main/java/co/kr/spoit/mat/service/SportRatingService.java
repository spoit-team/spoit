package co.kr.spoit.mat.service;

import co.kr.spoit.mat.dto.SportRatingResponse;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 종목별 레이팅 조회 진입점. 계산·초기 생성·반영은 수행하지 않는다. */
@Service
@Transactional(readOnly = true)
public class SportRatingService {

    private final SportRatingMapper ratingMapper;

    public SportRatingService(SportRatingMapper ratingMapper) {
        this.ratingMapper = ratingMapper;
    }

    /** 레이팅 행이 없으면 기본 MMR을 만들어 반환하지 않고 빈 Optional을 반환한다. */
    public Optional<SportRatingResponse> findByUserIdAndSportId(Long userId, Long sportId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId는 양수여야 합니다.");
        }
        if (sportId == null || sportId <= 0) {
            throw new IllegalArgumentException("sportId는 양수여야 합니다.");
        }
        return ratingMapper.findByUserIdAndSportId(userId, sportId)
                .map(SportRatingResponse::from);
    }
}
