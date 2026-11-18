package co.kr.spoit.admin.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import co.kr.spoit.admin.dto.SystemDashboard;
import co.kr.spoit.admin.dto.SystemErrorRecord;
import co.kr.spoit.admin.service.SystemResourceHistory.Sample;

/**
 * 시스템 모니터링 dashboard 모델을 실제 측정치로 구성한다.
 *
 * <p>화면 요청 시점에 표본을 한 번 기록하고, 이후 15초마다 스케줄로 표본을 이어 붙인다. 표본은
 * 메모리에만 남으며 15초 구간 중복은 억제된다. JVM·운영체제가 제공하지 않는 지표는 0으로
 * 위장하지 않고 {@code null}로 전달한다. 최근 오류 수치와 목록은 메모리 오류 버퍼에서 실제로
 * 집계하며 재시작하면 초기화된다. 신고 수집만 아직 범위가 아니므로 신고 관련 목록은 빈 상태로 둔다.</p>
 *
 * <p>최근 6시간의 차트 막대는 조회 시점부터 거슬러 10분 구간 36칸으로 나누고, 각 칸에 들어온
 * 15초 표본을 CPU·힙 평균으로 압축한다. 평균은 실제 측정값만 대상으로 하며, 한 구간의 값이 모두
 * 미지원이면 0으로 채우지 않고 {@code null}로 남긴다. 측정 표본이 없는 구간도 빠뜨리지 않고 라벨만
 * 있는 빈 칸으로 남긴다. CPU 피크는 압축된 평균이 아니라 원본 15초 표본 기준이다.</p>
 */
@Service
public class SystemMonitoringService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter COLLECTED_AT_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    /** 화면 차트 막대 하나가 담당하는 구간이다. */
    static final Duration CHART_BUCKET = Duration.ofMinutes(10);

    /** 최근 6시간 / 10분 = 36개. 화면에 표시할 막대 칸 수다. */
    static final int MAX_BARS = 36;

    private static final Duration CHART_WINDOW = Duration.ofHours(6);

    private static final String STATUS_UP = "정상";
    private static final String TONE_GOOD = "good";
    private static final String TONE_BAD = "bad";

    /** dashboard의 최근 오류 카드가 보여 주는 범위(시간)다. */
    static final int RECENT_ERROR_HOURS = 1;

    /** dashboard 최근 오류 표에 올리는 최대 행 수. */
    static final int RECENT_ERROR_ROWS = 10;

    private final SystemResourceProbe resourceProbe;
    private final SystemResourceHistory history;
    private final DatabaseAvailabilityProbe databaseProbe;
    private final SystemErrorLogBuffer errorLogBuffer;
    private final Clock clock;

    @Autowired
    public SystemMonitoringService(SystemResourceProbe resourceProbe, SystemResourceHistory history,
            DatabaseAvailabilityProbe databaseProbe, SystemErrorLogBuffer errorLogBuffer) {
        this(resourceProbe, history, databaseProbe, errorLogBuffer, Clock.system(ZONE));
    }

    /** 테스트에서 시각을 고정하기 위한 생성자다. */
    SystemMonitoringService(SystemResourceProbe resourceProbe, SystemResourceHistory history,
            DatabaseAvailabilityProbe databaseProbe, SystemErrorLogBuffer errorLogBuffer, Clock clock) {
        this.resourceProbe = resourceProbe;
        this.history = history;
        this.databaseProbe = databaseProbe;
        this.errorLogBuffer = errorLogBuffer;
        this.clock = clock;
    }

    /** 15초 간격으로 자원 표본을 수집한다. 화면 요청 여부와 무관하게 이어진다. */
    @Scheduled(fixedRate = 15_000L)
    public void collectSample() {
        LocalDateTime now = LocalDateTime.now(clock);
        history.record(now, resourceProbe.cpuPercent(), resourceProbe.heapPercent());
    }

    /**
     * 현재 시점의 dashboard 모델을 만든다.
     *
     * <p>첫 요청에서도 차트가 비지 않도록 조회 직전에 표본을 확보한다. 스케줄 수집과 같은 15초
     * 구간이면 저장소가 중복을 억제하므로 표본이 겹치지 않는다.</p>
     */
    public SystemDashboard currentDashboard() {
        LocalDateTime now = LocalDateTime.now(clock);
        Integer cpuPercent = resourceProbe.cpuPercent();
        Integer memoryPercent = resourceProbe.heapPercent();
        // 첫 요청에서도 차트가 비지 않도록 조회 시점 표본을 확보한다(같은 15초 구간이면 중복 저장 없음).
        history.record(now, cpuPercent, memoryPercent);

        Integer diskPercent = resourceProbe.diskPercent();
        boolean databaseReachable = databaseProbe.isReachable();
        // 조회 시점 기준 12시간 유효기간을 적용한 원본 15초 표본만 쓴다.
        List<Sample> samples = history.samples(now);
        List<SystemDashboard.ServiceStatus> services = services(databaseReachable);
        // 최근 오류 수/목록은 메모리 버퍼(재시작 시 초기화)에서 계산한다. JVM·DB 모니터링은 그대로 둔다.
        var recentErrors = recentErrors(now);

        return new SystemDashboard(
                now.format(COLLECTED_AT_FORMAT),
                STATUS_UP,
                formatUptime(resourceProbe.uptimeMillis()),
                cpuPercent,
                cpuPeak(samples),
                memoryPercent,
                resourceProbe.heapCapacityLabel(),
                diskPercent,
                recentErrors.count(),
                recentErrors.attention(),
                // 모든 서비스 probe가 정상일 때만 true다. 수집하지 않은 상태에서 정상으로 표시하지 않는다.
                services.stream().allMatch(service -> TONE_GOOD.equals(service.getTone())),
                bars(samples, now),
                services,
                recentErrors.rows(),
                List.of());
    }

    /**
     * 최근 1시간 오류를 dashboard 표 행으로 만든다.
     *
     * <p>{@code ERROR}만 세고 {@code WARN}은 제외한다. 같은 서비스·코드·요약은 하나로 묶는데,
     * 데이터는 메모리 버퍼에서 오므로 화면 요약은 마스킹된 값이다.</p>
     */
    private RecentErrors recentErrors(LocalDateTime now) {
        List<SystemErrorRecord> collected = errorLogBuffer.recentSince(now.minusHours(RECENT_ERROR_HOURS));
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.Map<String, SystemErrorRecord> first = new java.util.LinkedHashMap<>();
        for (SystemErrorRecord record : collected) {
            if (!"ERROR".equalsIgnoreCase(record.level())) {
                continue;
            }
            String key = record.service() + '\u0000' + record.code() + '\u0000' + record.summary();
            counts.merge(key, 1, Integer::sum);
            first.putIfAbsent(key, record);
        }
        List<Object> rows = new java.util.ArrayList<>();
        int attention = 0;
        for (java.util.Map.Entry<String, SystemErrorRecord> entry : first.entrySet()) {
            SystemErrorRecord record = entry.getValue();
            int count = counts.get(entry.getKey());
            attention += count;
            if (rows.size() >= RECENT_ERROR_ROWS) {
                continue;
            }
            rows.add(new SystemDashboard.RecentError(
                    SystemErrorLogBuffer.timeText(record.time()),
                    "오류",
                    TONE_BAD,
                    record.service(),
                    record.summary(),
                    record.code(),
                    count));
        }
        return new RecentErrors(attention, attention, rows);
    }

    /** dashboard 최근 오류 카드에 필요한 값 묶음이다. */
    private record RecentErrors(int count, int attention, List<Object> rows) {
    }

    /**
     * 차트는 최근 6시간을 조회 시점부터 거슬러 10분 구간 36칸으로 나누고, 각 칸에 들어온 표본을 평균으로
     * 압축한다.
     *
     * <p>측정 불가({@code null})는 0으로 취급하지 않고 평균에서 제외한다. 한 구간의 측정값이 모두
     * 없으면 그 막대의 값은 {@code null}이다. 측정 표본이 없는 구간도 건너뛰지 않고 라벨만 있는 빈
     * 칸으로 남기므로, 항상 최대 {@link #MAX_BARS}개의 칸이 시간 순서대로 이어진다.</p>
     */
    private static List<SystemDashboard.Bar> bars(List<Sample> samples, LocalDateTime now) {
        long bucketSeconds = CHART_BUCKET.getSeconds();
        long[] cpuSums = new long[MAX_BARS];
        int[] cpuCounts = new int[MAX_BARS];
        long[] memorySums = new long[MAX_BARS];
        int[] memoryCounts = new int[MAX_BARS];
        for (Sample sample : samples) {
            LocalDateTime time = sample.bucketStart();
            if (time.isBefore(now.minus(CHART_WINDOW)) || time.isAfter(now)) {
                continue;
            }
            int index = (int) (Duration.between(chartBucketStart(now, 0), time).getSeconds() / bucketSeconds);
            if (index < 0) {
                continue;
            }
            if (index >= MAX_BARS) {
                index = MAX_BARS - 1;
            }
            if (sample.cpuPercent() != null) {
                cpuSums[index] += sample.cpuPercent();
                cpuCounts[index]++;
            }
            if (sample.memoryPercent() != null) {
                memorySums[index] += sample.memoryPercent();
                memoryCounts[index]++;
            }
        }
        List<SystemDashboard.Bar> bars = new ArrayList<>(MAX_BARS);
        for (int index = 0; index < MAX_BARS; index++) {
            bars.add(bar(chartBucketStart(now, index), cpuSums[index], cpuCounts[index], memorySums[index],
                    memoryCounts[index]));
        }
        return bars;
    }

    private static SystemDashboard.Bar bar(LocalDateTime bucketStart, long cpuSum, int cpuCount, long memorySum,
            int memoryCount) {
        Integer cpuPercent = null;
        if (cpuCount != 0) {
            cpuPercent = (int) Math.round((double) cpuSum / cpuCount);
        }
        Integer memoryPercent = null;
        if (memoryCount != 0) {
            memoryPercent = (int) Math.round((double) memorySum / memoryCount);
        }
        return new SystemDashboard.Bar(
                cpuPercent,
                memoryPercent,
                SystemResourceHistory.label(bucketStart));
    }

    /**
     * 조회 시점 {@code now}부터 거슬러 {@code index}번째(0이 가장 오래된 칸) 차트 구간의 시작 시각이다.
     *
     * <p>가장 오래된 칸은 {@code now - 6시간}, 마지막 칸은 {@code now - 10분}에서 시작한다. 조회
     * 시점이 기준이므로 15초 갱신마다 경계가 함께 밀리며, 6시간이 36칸으로 빈틈없이 이어진다.</p>
     */
    static LocalDateTime chartBucketStart(LocalDateTime now, int index) {
        return now.minus(CHART_BUCKET.multipliedBy(MAX_BARS - index));
    }

    /** CPU 피크는 수집된 원본 15초 표본 중 측정된 값의 최대치다. 측정 표본이 없으면 null이다. */
    private static Integer cpuPeak(List<Sample> samples) {
        Integer peak = null;
        for (Sample sample : samples) {
            if (sample.cpuPercent() != null && (peak == null || sample.cpuPercent() > peak)) {
                peak = sample.cpuPercent();
            }
        }
        return peak;
    }

    private static List<SystemDashboard.ServiceStatus> services(boolean databaseReachable) {
        String databaseStatus;
        String databaseTone;
        if (databaseReachable) {
            databaseStatus = "연결 정상";
            databaseTone = TONE_GOOD;
        } else {
            databaseStatus = "연결 확인 실패";
            databaseTone = TONE_BAD;
        }
        return List.of(
                new SystemDashboard.ServiceStatus("API 서버", STATUS_UP, TONE_GOOD),
                new SystemDashboard.ServiceStatus("데이터베이스", databaseStatus, databaseTone));
    }

    private static String formatUptime(long uptimeMillis) {
        long totalMinutes = uptimeMillis / 60_000L;
        long days = totalMinutes / (60L * 24L);
        long hours = totalMinutes % (60L * 24L) / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0) {
            return days + "일 " + hours + "시간";
        }
        if (hours > 0) {
            return hours + "시간 " + minutes + "분";
        }
        return minutes + "분";
    }
}
