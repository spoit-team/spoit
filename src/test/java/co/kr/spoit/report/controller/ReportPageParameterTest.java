package co.kr.spoit.report.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 시설·시스템 신고 화면이 공통으로 쓰는 페이지 파라미터 해석 결과를 고정한다.
 *
 * <p>누락·공백·비정수·음수는 0, {@code int} 초과는 정수 상한, {@code long} 범위 초과는
 * 정수로 해석할 수 없으므로 0이다. 상한 정규화는 서비스가 맡는다.</p>
 */
class ReportPageParameterTest {

    @Test
    @DisplayName("누락·공백·비정수·음수는 0페이지로 본다")
    void invalidValuesBecomeZero() {
        assertThat(ReportPageParameter.parse(null)).isZero();
        assertThat(ReportPageParameter.parse("")).isZero();
        assertThat(ReportPageParameter.parse("   ")).isZero();
        assertThat(ReportPageParameter.parse("abc")).isZero();
        assertThat(ReportPageParameter.parse("1.5")).isZero();
        assertThat(ReportPageParameter.parse("-4")).isZero();
    }

    @Test
    @DisplayName("숫자는 그대로, int 초과는 정수 상한으로 자르고 long 초과는 0이다")
    void numericValuesNormalize() {
        assertThat(ReportPageParameter.parse("0")).isZero();
        assertThat(ReportPageParameter.parse("2")).isEqualTo(2);
        assertThat(ReportPageParameter.parse(" 7 ")).isEqualTo(7);
        assertThat(ReportPageParameter.parse("999999999999")).isEqualTo(Integer.MAX_VALUE);
        assertThat(ReportPageParameter.parse("9223372036854775808")).isZero();
    }
}
