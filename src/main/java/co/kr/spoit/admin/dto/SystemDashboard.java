package co.kr.spoit.admin.dto;

import java.util.List;

/**
 * 시스템 모니터링 dashboard 화면 모델이다.
 *
 * <p>값은 조회 시점에 실제로 수집된 측정치다. JVM·운영체제가 제공하지 않는 지표(프로세스 CPU,
 * 파일시스템 사용률)는 0으로 위장하지 않고 {@code null}로 남긴다. 오류 로그·신고 수집은 아직
 * 범위가 아니므로 관련 수치는 {@code null}, 목록은 빈 목록이다.</p>
 */
public class SystemDashboard {

    private final String collectedAt;
    private final String apiStatus;
    private final String apiUptime;
    private final Integer cpuPercent;
    private final Integer cpuPeak;
    private final Integer memoryPercent;
    private final String memoryCapacity;
    private final Integer diskPercent;
    private final Integer recentErrorCount;
    private final Integer recentErrorAttention;
    private final boolean allHealthy;
    private final List<Bar> bars;
    private final List<ServiceStatus> services;
    private final List<Object> recentErrors;
    private final List<Object> recentReports;

    public SystemDashboard(String collectedAt, String apiStatus, String apiUptime, Integer cpuPercent,
            Integer cpuPeak, Integer memoryPercent, String memoryCapacity, Integer diskPercent,
            Integer recentErrorCount, Integer recentErrorAttention, boolean allHealthy, List<Bar> bars,
            List<ServiceStatus> services, List<Object> recentErrors, List<Object> recentReports) {
        this.collectedAt = collectedAt;
        this.apiStatus = apiStatus;
        this.apiUptime = apiUptime;
        this.cpuPercent = cpuPercent;
        this.cpuPeak = cpuPeak;
        this.memoryPercent = memoryPercent;
        this.memoryCapacity = memoryCapacity;
        this.diskPercent = diskPercent;
        this.recentErrorCount = recentErrorCount;
        this.recentErrorAttention = recentErrorAttention;
        this.allHealthy = allHealthy;
        this.bars = List.copyOf(bars);
        this.services = List.copyOf(services);
        this.recentErrors = List.copyOf(recentErrors);
        this.recentReports = List.copyOf(recentReports);
    }

    public String getCollectedAt() {
        return collectedAt;
    }

    public String getApiStatus() {
        return apiStatus;
    }

    public String getApiUptime() {
        return apiUptime;
    }

    public Integer getCpuPercent() {
        return cpuPercent;
    }

    public Integer getCpuPeak() {
        return cpuPeak;
    }

    public Integer getMemoryPercent() {
        return memoryPercent;
    }

    public String getMemoryCapacity() {
        return memoryCapacity;
    }

    public Integer getDiskPercent() {
        return diskPercent;
    }

    /** 오류 수집은 이번 범위가 아니므로 값이 없다. */
    public Integer getRecentErrorCount() {
        return recentErrorCount;
    }

    /** 오류 수집은 이번 범위가 아니므로 값이 없다. */
    public Integer getRecentErrorAttention() {
        return recentErrorAttention;
    }

    public boolean isAllHealthy() {
        return allHealthy;
    }

    public List<Bar> getBars() {
        return bars;
    }

    public List<ServiceStatus> getServices() {
        return services;
    }

    public List<Object> getRecentErrors() {
        return recentErrors;
    }

    public List<Object> getRecentReports() {
        return recentReports;
    }

    /** 차트 한 칸. 실제 수집된 표본 하나에 대응한다. */
    public static class Bar {

        private final Integer cpuHeight;
        private final Integer memoryHeight;
        private final String label;

        public Bar(Integer cpuHeight, Integer memoryHeight, String label) {
            this.cpuHeight = cpuHeight;
            this.memoryHeight = memoryHeight;
            this.label = label;
        }

        public Integer getCpuHeight() {
            return cpuHeight;
        }

        public Integer getMemoryHeight() {
            return memoryHeight;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 서비스 상태 목록의 한 줄. API 프로세스와 DB 연결을 구분해 담는다. */
    public static class ServiceStatus {

        private final String name;
        private final String detail;
        private final String tone;

        public ServiceStatus(String name, String detail, String tone) {
            this.name = name;
            this.detail = detail;
            this.tone = tone;
        }

        public String getName() {
            return name;
        }

        public String getDetail() {
            return detail;
        }

        public String getTone() {
            return tone;
        }
    }
}
