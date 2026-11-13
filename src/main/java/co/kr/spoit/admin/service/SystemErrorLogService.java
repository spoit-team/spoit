package co.kr.spoit.admin.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import co.kr.spoit.admin.dto.SystemErrorFilters;
import co.kr.spoit.admin.dto.SystemErrorLog;
import co.kr.spoit.admin.dto.SystemErrorLogPage;
import co.kr.spoit.admin.dto.SystemErrorMetrics;
import co.kr.spoit.admin.dto.SystemErrorRecord;

/**
 * 서버·오류 로그 화면 모델을 만든다.
 *
 * <p>수집은 {@link SystemErrorLogBuffer}가 맡고, 이 서비스는 검색 조건 정규화·기간 필터·같은
 * 오류 묶기·지표 계산만 한다. 수집하지 않는 값(평균 응답 시간)은 0으로 위장하지 않고 {@code null}로
 * 둔다.</p>
 */
@Service
public class SystemErrorLogService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 화면이 제공하는 기간(시간) 선택지다. */
    static final int PERIOD_DAY = 24;
    static final int PERIOD_WEEK = 168;

    /**
     * 한 화면에 표시할 최대 행 수. 버퍼가 보관하는 최대 건수와 같게 두어, 같은 오류 묶기가
     * 일어나지 않는 경우에도 버퍼에 있는 모든 기록을 화면·CSV에서 볼 수 있게 한다.
     */
    static final int MAX_ROWS = SystemErrorLogBuffer.CAPACITY;

    private static final String ALL = "all";
    private static final String SEVERITY_ERROR = "error";
    private static final String SEVERITY_WARN = "warn";
    private static final String SEVERITY_INFO = "info";

    private final SystemErrorLogBuffer buffer;
    private final Clock clock;

    @Autowired
    public SystemErrorLogService(SystemErrorLogBuffer buffer) {
        this(buffer, Clock.system(ZONE));
    }

    /** 테스트에서 시각을 고정하기 위한 생성자다. */
    SystemErrorLogService(SystemErrorLogBuffer buffer, Clock clock) {
        this.buffer = buffer;
        this.clock = clock;
    }

    /** 검색 조건을 반영한 화면 모델을 만든다. */
    public SystemErrorLogPage load(String q, String severity, String period) {
        String normalizedSeverity = normalizeSeverity(severity);
        int normalizedPeriod = normalizePeriod(period);
        String keyword = normalizeKeyword(q);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime since = now.minusHours(normalizedPeriod);

        List<SystemErrorRecord> collected = buffer.recentSince(since);
        List<SystemErrorLog> matched = new ArrayList<>();
        int errorCount = 0;
        int warnCount = 0;
        LocalDateTime lastCollected = null;
        for (SystemErrorRecord record : collected) {
            if (lastCollected == null || record.time().isAfter(lastCollected)) {
                lastCollected = record.time();
            }
            if (SEVERITY_ERROR.equalsIgnoreCase(record.level())) {
                errorCount++;
            } else if ("WARN".equalsIgnoreCase(record.level())) {
                warnCount++;
            }
            if (!matchesSeverity(record, normalizedSeverity) || !matchesKeyword(record, keyword)) {
                continue;
            }
            matched.add(toRow(record));
        }

        List<SystemErrorLog> rows = group(matched);
        SystemErrorMetrics metrics = new SystemErrorMetrics(
                errorCount,
                warnCount,
                errorCount,
                // 응답 시간은 수집 범위가 아니므로 값을 만들지 않는다.
                null,
                lastCollected == null ? null : SystemErrorLogBuffer.timeText(lastCollected));
        return new SystemErrorLogPage(metrics,
                new SystemErrorFilters(keyword, normalizedSeverity, normalizedPeriod), rows);
    }

    /**
     * 같은 서비스·레벨·코드·요약의 기록을 하나로 묶어 횟수와 마지막 발생 시각만 남긴다.
     *
     * <p>입력은 최신 순이므로 묶음의 첫 기록이 마지막 발생 시각이 된다. 순서는 처음 등장한 순서를
     * 유지해 화면이 최신 오류부터 보여 준다.</p>
     */
    static List<SystemErrorLog> group(List<SystemErrorLog> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, SystemErrorLog> first = new LinkedHashMap<>();
        for (SystemErrorLog row : rows) {
            String key = row.getService() + '\u0000' + row.getSeverity() + '\u0000' + row.getCode()
                    + '\u0000' + row.getSummary();
            counts.merge(key, 1, Integer::sum);
            first.putIfAbsent(key, row);
        }
        List<SystemErrorLog> grouped = new ArrayList<>(first.size());
        for (Map.Entry<String, SystemErrorLog> entry : first.entrySet()) {
            SystemErrorLog row = entry.getValue();
            int count = counts.get(entry.getKey());
            grouped.add(count == 1 ? row : new SystemErrorLog(row.getTime(), row.getSeverity(),
                    row.getSeverityLabel(), row.getSeverityTone(), row.getService(), row.getCode(),
                    row.getSummary(), count, row.getDetail()));
            if (grouped.size() >= MAX_ROWS) {
                break;
            }
        }
        return grouped;
    }

    static SystemErrorLog toRow(SystemErrorRecord record) {
        boolean warning = "WARN".equalsIgnoreCase(record.level());
        return new SystemErrorLog(
                SystemErrorLogBuffer.timeText(record.time()),
                warning ? SEVERITY_WARN : SEVERITY_ERROR,
                warning ? "경고" : "오류",
                warning ? "warn" : "bad",
                record.service(),
                record.code(),
                record.summary(),
                1,
                record.detail());
    }

    private static boolean matchesSeverity(SystemErrorRecord record, String severity) {
        if (ALL.equals(severity)) {
            return true;
        }
        if (SEVERITY_WARN.equals(severity)) {
            return "WARN".equalsIgnoreCase(record.level());
        }
        if (SEVERITY_INFO.equals(severity)) {
            // INFO는 수집하지 않는다. 화면 선택지와 수집 범위를 맞추기 위해 항상 비운다.
            return false;
        }
        return SEVERITY_ERROR.equals(severity) && "ERROR".equalsIgnoreCase(record.level());
    }

    private static boolean matchesKeyword(SystemErrorRecord record, String keyword) {
        if (keyword == null) {
            return true;
        }
        String needle = SystemErrorLogBuffer.lower(keyword);
        return SystemErrorLogBuffer.lower(record.service()).contains(needle)
                || SystemErrorLogBuffer.lower(record.code()).contains(needle)
                || SystemErrorLogBuffer.lower(record.summary()).contains(needle)
                || SystemErrorLogBuffer.lower(record.detail()).contains(needle);
    }

    /** 알려진 심각도만 인정하고 나머지는 전체({@code all})로 되돌린다. */
    static String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return ALL;
        }
        String value = severity.strip().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case SEVERITY_ERROR, SEVERITY_WARN, SEVERITY_INFO -> value;
            default -> ALL;
        };
    }

    /** 알 수 없는 기간은 하루로 되돌린다. */
    static int normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return PERIOD_DAY;
        }
        try {
            int parsed = Integer.parseInt(period.strip());
            if (parsed == PERIOD_DAY || parsed == PERIOD_WEEK) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // 아래 기본값으로 되돌린다.
        }
        return PERIOD_DAY;
    }

    private static String normalizeKeyword(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String value = q.strip();
        return value.length() > 100 ? value.substring(0, 100) : value;
    }
}
