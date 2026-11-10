package co.kr.spoit.club;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.club.dto.ClubSearchForm;

public class ClubSearchFormTest {

    @Test
    @DisplayName("검색어는 앞뒤 공백을 지우고, 빈 칸이면 조건에서 뺀다")
    void 검색어_정리() {
        ClubSearchForm form = new ClubSearchForm();
        form.setQ(" 강남 ");
        assertThat(form.getQuery()).isEqualTo("강남");
        form.setQ("  ");
        assertThat(form.getQuery()).isNull();
    }

    @Test
    @DisplayName("LIKE 검색어는 %, _, ! 앞에 !를 붙이고, 검색칸 표시는 입력 그대로 둔다")
    void 검색어_LIKE_이스케이프() {
        ClubSearchForm form = new ClubSearchForm();
        form.setQ(" 100%_! ");
        assertThat(form.getQuery()).isEqualTo("100%_!");
        assertThat(form.getLikeQuery()).isEqualTo("100!%!_!!");
        form.setQ("강남");
        assertThat(form.getLikeQuery()).isEqualTo("강남");
    }

    @Test
    @DisplayName("실력 단계를 MMR 구간으로 바꾸고, 잘못된 단계는 조건에서 뺀다")
    void 실력_단계_구간() {
        ClubSearchForm form = new ClubSearchForm();
        form.setLevel(1);
        assertThat(form.getMinMmr()).isNull();
        assertThat(form.getMaxMmr()).isEqualTo(1000);
        form.setLevel(3);
        assertThat(form.getMinMmr()).isEqualTo(1200);
        assertThat(form.getMaxMmr()).isEqualTo(1400);
        form.setLevel(5);
        assertThat(form.getMinMmr()).isEqualTo(1600);
        assertThat(form.getMaxMmr()).isNull();
        form.setLevel(9);
        assertThat(form.getMinMmr()).isNull();
        assertThat(form.getMaxMmr()).isNull();
    }

    @Test
    @DisplayName("첫 화면은 1장부터 page장까지 한 번에, 최대 5장, 잘못된 page는 1장")
    void 첫_화면_개수() {
        ClubSearchForm form = new ClubSearchForm();
        form.setPage(3);
        assertThat(form.getSize()).isEqualTo(36);
        assertThat(form.getSkip()).isZero();
        form.setPage(9);
        assertThat(form.getShownPage()).isEqualTo(5);
        assertThat(form.getSize()).isEqualTo(60);
        form.setPage(0);
        assertThat(form.getPage()).isEqualTo(1);
        assertThat(form.getSize()).isEqualTo(12);
        form.setPage(null);
        assertThat(form.getPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("다음 장 요청은 그 장 12개만, 앞 장들은 건너뜀")
    void 다음_장_개수() {
        ClubSearchForm form = new ClubSearchForm();
        form.setCardsOnly(true);
        form.setPage(3);
        assertThat(form.getSize()).isEqualTo(12);
        assertThat(form.getSkip()).isEqualTo(24);
        form.setPage(9);
        assertThat(form.getSize()).isEqualTo(12);
        assertThat(form.getSkip()).isEqualTo(96);
    }

}
