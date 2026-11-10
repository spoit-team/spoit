package co.kr.spoit.report.controller;

/**
 * 신고 화면들이 공통으로 쓰는 페이지 쿼리 파라미터 해석기다.
 *
 * <p>시설 관리자·시스템 관리자 화면 모두 페이지를 0부터 시작하는 정수로 받는다. 누락·공백·비정수·
 * 음수는 0으로 보고, {@code int} 범위를 넘는 값은 {@link Integer#MAX_VALUE}로 자른다. {@code long}
 * 범위마저 넘는 값은 정수로 해석할 수 없으므로 0이다. 상한 정규화는 서비스가 맡는다.</p>
 */
final class ReportPageParameter {

    private ReportPageParameter() {
    }

    /** 페이지 파라미터를 음수·비정수·범위 초과를 흡수해 안전한 정수로 해석한다. */
    static int parse(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            long parsed = Long.parseLong(value.strip());
            if (parsed < 0) {
                return 0;
            }
            // int 범위를 넘는 값은 안전 상한으로 자른다.
            return parsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parsed;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
