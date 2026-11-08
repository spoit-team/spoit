package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.match.dto.MatchCardView;

/** 매칭방 목록 상단 배경 사진 선택 규칙을 검증한다. */
class MatchServiceHeroImageTest {

    @Test
    @DisplayName("목록 위에서부터 사진이 있는 첫 매칭방의 사진을 배경으로 쓴다")
    void usesFirstCardWithPhoto() {
        List<MatchCardView> cards = List.of(card(1L, null), card(2L, "/uploads/b.jpg"), card(3L, "/uploads/c.jpg"));

        assertThat(MatchService.heroImageUrl(cards)).isEqualTo("/uploads/b.jpg");
    }

    @Test
    @DisplayName("사진이 있는 방이 없으면 기본 배경(null)")
    void returnsNullWithoutPhotos() {
        assertThat(MatchService.heroImageUrl(List.of(card(1L, null)))).isNull();
        assertThat(MatchService.heroImageUrl(List.of())).isNull();
    }

    private static MatchCardView card(Long matchId, String photoUrl) {
        return MatchCardView.builder().matchId(matchId).photoUrl(photoUrl).build();
    }
}
