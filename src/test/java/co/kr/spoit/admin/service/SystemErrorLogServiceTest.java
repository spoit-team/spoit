package co.kr.spoit.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.admin.dto.SystemErrorLogPage;
import co.kr.spoit.admin.dto.SystemErrorRecord;

/**
 * 오류 로그 화면 모델이 검색 조건·기간·심각도·묶기를 올바르게 적용하는지 검증한다.
 *
 * <p>수집하지 않는 평균 응답 시간은 {@code null}로 남기고, 같은 오류는 하나로 묶어 횟수를 센다.
 * 고정된 시각 기록을 버퍼에 직접 넣어 기간 경계를 결정적으로 확인한다.</p>
 */
class SystemErrorLogServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);

    private final SystemErrorLogBuffer buffer = new SystemErrorLogBuffer();
    private final SystemErrorLogService service = new SystemErrorLogService(buffer,
            Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));

    @Test
    @DisplayName("기본 조회는 최근 24시간 ERROR/WARN을 모두 보여 주고 평균 응답은 null이다")
    void loadsDefaultPeriod() {
        buffer.recordForTest(record(NOW.minusHours(2), "ERROR", "match", "java.lang.IllegalStateException", "오류 A"));
        buffer.recordForTest(record(NOW.minusHours(3), "WARN", "club", "LOG", "경고 B"));

        SystemErrorLogPage page = service.load(null, null, null);

        assertThat(page.getFilters().getPeriod()).isEqualTo(SystemErrorLogService.PERIOD_DAY);
        assertThat(page.getFilters().getSeverity()).isEqualTo("all");
        assertThat(page.getErrors()).hasSize(2);
        assertThat(page.getMetrics().getCount()).isEqualTo(1);
        assertThat(page.getMetrics().getWarningCount()).isEqualTo(1);
        assertThat(page.getMetrics().getUncheckedCount()).isEqualTo(1);
        assertThat(page.getMetrics().getAverageResponse()).isNull();
        assertThat(page.getMetrics().getLastCollected()).isEqualTo("2026-11-10 10:00:00");
        assertThat(page.getMetrics().getOpenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("기간(시간)을 벗어난 기록은 제외하고 알 수 없는 조건은 기본값으로 되돌린다")
    void filtersByPeriod() {
        buffer.recordForTest(record(NOW.minusHours(48), "ERROR", "match", "CODE", "이틀 전"));

        SystemErrorLogPage week = service.load(null, "bogus", "168");
        SystemErrorLogPage day = service.load(null, "all", "999");

        assertThat(week.getFilters().getPeriod()).isEqualTo(SystemErrorLogService.PERIOD_WEEK);
        assertThat(week.getFilters().getSeverity()).isEqualTo("all");
        assertThat(week.getErrors()).hasSize(1);
        assertThat(day.getFilters().getPeriod()).isEqualTo(SystemErrorLogService.PERIOD_DAY);
        assertThat(day.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("심각도·검색어 조건을 적용한다")
    void filtersBySeverityAndKeyword() {
        buffer.recordForTest(record(NOW.minusMinutes(10), "ERROR", "match", "java.lang.IllegalStateException", "경기 실패"));
        buffer.recordForTest(record(NOW.minusMinutes(20), "WARN", "club", "LOG", "동호회 경고"));

        SystemErrorLogPage warnOnly = service.load(null, "warn", "24");
        SystemErrorLogPage byText = service.load("club", null, "24");

        assertThat(warnOnly.getErrors()).singleElement()
                .satisfies(row -> assertThat(row.getSeverityLabel()).isEqualTo("경고"));
        assertThat(byText.getErrors()).singleElement()
                .satisfies(row -> assertThat(row.getService()).isEqualTo("club"));
        assertThat(warnOnly.getMetrics().getCount()).isEqualTo(1);
        assertThat(warnOnly.getMetrics().getWarningCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 서비스·레벨·코드·요약은 하나로 묶어 횟수를 센다")
    void groupsIdenticalErrors() {
        buffer.recordForTest(record(NOW.minusMinutes(30), "ERROR", "match", "CODE", "같은 오류"));
        buffer.recordForTest(record(NOW.minusMinutes(20), "ERROR", "match", "CODE", "같은 오류"));
        buffer.recordForTest(record(NOW.minusMinutes(10), "ERROR", "match", "CODE", "같은 오류"));
        buffer.recordForTest(record(NOW.minusMinutes(5), "ERROR", "match", "OTHER", "다른 오류"));

        SystemErrorLogPage page = service.load(null, null, "24");

        assertThat(page.getErrors()).hasSize(2);
        // 최신 순으로 처음 등장한 묶음이 먼저다.
        assertThat(page.getErrors().get(0).getSummary()).isEqualTo("다른 오류");
        assertThat(page.getErrors().get(1).getSummary()).isEqualTo("같은 오류");
        assertThat(page.getErrors().get(1).getCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("주지 않은 조건은 전체·하루로 되돌리고 info는 수집 범위 밖이라 항상 빈다")
    void filtersUnknownSeverityToAll() {
        buffer.recordForTest(record(NOW.minusMinutes(10), "ERROR", "match", "CODE", "오류"));

        SystemErrorLogPage info = service.load(null, "info", "24");
        SystemErrorLogPage bogus = service.load(null, "bogus", "24");

        assertThat(info.getFilters().getSeverity()).isEqualTo("info");
        // INFO는 수집하지 않으므로 선택해도 결과가 비는 것이 정상이다.
        assertThat(info.getErrors()).isEmpty();
        assertThat(bogus.getFilters().getSeverity()).isEqualTo("all");
        assertThat(bogus.getErrors()).hasSize(1);
    }

    @Test
    @DisplayName("같은 오류가 아닌 기록은 200건을 넘어도 버퍼 상한까지 모두 보여 준다")
    void returnsMoreThanTwoHundredDistinctRows() {
        // 화면 절단 상한(과거 200)이 아니라 버퍼 상한까지 노출되는지 확인한다.
        // 버퍼는 추가 순서를 시간 순서로 가정하므로 오래된 기록부터 넣는다.
        for (int i = 250; i >= 1; i--) {
            buffer.recordForTest(record(NOW.minusMinutes(i), "ERROR", "match", "CODE-" + i, "고유 오류 " + i));
        }

        SystemErrorLogPage page = service.load(null, null, "24");

        assertThat(page.getErrors()).hasSize(250);
        assertThat(page.getErrors().get(0).getSummary()).isEqualTo("고유 오류 1");
        assertThat(page.getErrors().get(249).getSummary()).isEqualTo("고유 오류 250");
    }

    @Test
    @DisplayName("버퍼 상한을 넘겨 수집된 기록은 최신 500건까지만 보여 준다")
    void keepsAtMostBufferCapacityRows() {
        int total = SystemErrorLogBuffer.CAPACITY + 20;
        // 오래된 기록부터 넣어 버퍼가 마지막 500건(고유 오류 1..500)을 남기게 한다.
        for (int i = total; i >= 1; i--) {
            buffer.recordForTest(record(NOW.minusSeconds(i), "ERROR", "match", "CODE-" + i, "고유 오류 " + i));
        }

        SystemErrorLogPage page = service.load(null, null, "24");

        assertThat(page.getErrors()).hasSize(SystemErrorLogBuffer.CAPACITY);
        // 최신 기록이 먼저 나오고, 버퍼에서 밀려난 오래된 기록은 보이지 않는다.
        assertThat(page.getErrors().get(0).getSummary()).isEqualTo("고유 오류 1");
        assertThat(page.getErrors().get(SystemErrorLogBuffer.CAPACITY - 1).getSummary())
                .isEqualTo("고유 오류 " + SystemErrorLogBuffer.CAPACITY);
    }

    @Test
    @DisplayName("빈 버퍼는 0건·수집 기록 없음으로 표현한다")
    void emptyBufferHasNoLastCollected() {
        SystemErrorLogPage page = service.load(null, null, null);

        assertThat(page.getErrors()).isEmpty();
        assertThat(page.getMetrics().getCount()).isZero();
        assertThat(page.getMetrics().getLastCollected()).isNull();
    }

    private static SystemErrorRecord record(LocalDateTime time, String level, String service, String code,
            String summary) {
        return new SystemErrorRecord(time, level, "co.kr.spoit." + service + ".Test", service, code, summary,
                summary);
    }
}
