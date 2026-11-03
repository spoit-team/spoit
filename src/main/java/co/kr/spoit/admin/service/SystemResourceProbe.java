package co.kr.spoit.admin.service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Method;

import org.springframework.stereotype.Component;

/**
 * JVM·운영체제에서 직접 읽을 수 있는 시스템 자원 측정치를 제공한다.
 *
 * <p>JDK 표준 API만 사용한다. 측정이 불가능한 지표는 0으로 위장하지 않고 {@code null}로
 * 반환해 화면이 "수집되지 않음"을 구분할 수 있게 한다.</p>
 */
@Component
public class SystemResourceProbe {

    /** 프로세스 CPU 사용률을 노출하는 JDK 인터페이스 이름이다. 미지원 JVM에서는 조회에 실패한다. */
    private static final String OS_BEAN_TYPE = "com.sun.management.OperatingSystemMXBean";

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    private static final long BYTES_PER_GIGABYTE = BYTES_PER_MEGABYTE * 1024L;

    /**
     * 이 프로세스의 CPU 사용률(%)을 반환한다.
     *
     * <p>JDK가 프로세스 CPU 시간을 제공하지 않으면 {@code null}이다. 지원 여부를 알 수 없으므로
     * 0%로 대체하지 않는다.</p>
     */
    public Integer cpuPercent() {
        Double load = processCpuLoad();
        if (load == null || load.isNaN() || load < 0) {
            return null;
        }
        return toPercent(load * 100.0);
    }

    /**
     * JVM 힙 사용률(%)이다.
     *
     * <p>최대 힙 크기를 알 수 없으면 사용률을 계산할 수 없으므로 0%로 위장하지 않고
     * {@code null}을 반환한다. {@code maxMemory()}가 {@code Long.MAX_VALUE}인 JVM은 상한이
     * 사실상 없는 것으로 보아 역시 {@code null}이다.</p>
     */
    public Integer heapPercent() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0 || max == Long.MAX_VALUE) {
            return null;
        }
        long used = runtime.totalMemory() - runtime.freeMemory();
        return (int) Math.min(100L, Math.max(0L, Math.round(used * 100.0 / max)));
    }

    /** JVM 최대 힙 크기를 화면 표기용 문자열로 반환한다. 알 수 없으면 {@code null}이다. */
    public String heapCapacityLabel() {
        long max = Runtime.getRuntime().maxMemory();
        if (max <= 0 || max == Long.MAX_VALUE) {
            return null;
        }
        if (max >= BYTES_PER_GIGABYTE) {
            return String.format("%.1fGB", max / (double) BYTES_PER_GIGABYTE);
        }
        return (max / BYTES_PER_MEGABYTE) + "MB";
    }

    /**
     * 기본 작업 디렉터리가 속한 파일시스템의 사용률(%)을 반환한다.
     *
     * <p>파일시스템 용량 조회를 지원하지 않는 환경에서는 {@code null}이다.</p>
     */
    public Integer diskPercent() {
        File root = new File(".").getAbsoluteFile();
        long total = root.getTotalSpace();
        long usable = root.getUsableSpace();
        if (total <= 0) {
            return null;
        }
        long used = total - Math.max(0L, usable);
        return toPercent(used * 100.0 / total);
    }

    /** 애플리케이션 프로세스가 살아 있는 시간(ms)이다. */
    public long uptimeMillis() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        return runtimeBean.getUptime();
    }

    private static Double processCpuLoad() {
        Object bean = ManagementFactory.getOperatingSystemMXBean();
        try {
            Class<?> type = Class.forName(OS_BEAN_TYPE);
            if (!type.isInstance(bean)) {
                return null;
            }
            Method method = type.getMethod("getProcessCpuLoad");
            Object value = method.invoke(bean);
            return value instanceof Double load ? load : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    private static Integer toPercent(double value) {
        return (int) Math.min(100L, Math.max(0L, Math.round(value)));
    }
}
