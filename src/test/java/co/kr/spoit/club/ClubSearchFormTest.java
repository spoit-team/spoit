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
    @DisplayName("실력 단계를 MMR 구간으로 바꾸고, 잘못된 단계는 조건에서 뺀다")
    void 실력_단계_구간() {
        ClubSearchForm form = new ClubSearchForm();
        form.setLevel(1);
        assertThat(form.getMinMmr()).isNull();
        assertThat(form.getMaxMmr()).isEqualTo(900);
        form.setLevel(3);
        assertThat(form.getMinMmr()).isEqualTo(1050);
        assertThat(form.getMaxMmr()).isEqualTo(1200);
        form.setLevel(5);
        assertThat(form.getMinMmr()).isEqualTo(1350);
        assertThat(form.getMaxMmr()).isNull();
        form.setLevel(9);
        assertThat(form.getMinMmr()).isNull();
        assertThat(form.getMaxMmr()).isNull();
    }
}
