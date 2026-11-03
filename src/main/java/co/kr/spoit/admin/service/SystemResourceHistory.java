package co.kr.spoit.admin.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

/**
 * 시스템 자원 표본을 메모리에 보관하는 저장소다.
 *
 * <p>15초 단위 구간마다 표본 1개만 유지해 최근 12시간(최대 2,880개)까지만 남긴다. 애플리케이션이
 * 재시작되면 사라지며, 외부 저장소나 파일에는 쓰지 않는다. 동시에 들어온 수집·조회 요청이 서로의
 * 자료구조를 깨지 않도록 모든 접근을 이 인스턴스로 동기화한다. 구간 시작 시각을 키로 쓰는 정렬
 * 맵이라 요청 스레드와 스케줄 스레드가 뒤섞여 기록해도 순서와 중복 억제가 유지된다.</p>
 *
 * <p>표본 수 상한만으로는 수집이 뜸한 구간(예: 트래픽이 없다가 몇 시간 뒤 재개)에서 12시간을
 * 넘긴 표본이 남는다. 그래서 개수 상한과 별개로 <b>조회 시점 기준 12시간 유효기간</b>을 적용해
 * 만료 표본을 버린다. 경계 판정에는 조회 시점을 명시적으로 받아 결정적으로 검증할 수 있게 한다.</p>
 *
 * <p>15초 표본은 화면 차트에 그대로 쓰기에는 너무 많으므로, 차트용 5분 집계는 서비스 계층이
 * 표본을 읽어서 만든다. 이 저장소는 원본 15초 표본만 보관한다.</p>
 */
@Component
public class SystemResourceHistory {

    /** 수집 간격이자 중복 억제 단위다. */
    public static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(15);

    /** 표본 유효기간이다. */
    public static final Duration RETENTION = Duration.ofHours(12);

    /** 12시간 / 15초 = 2,880개. */
    public static final int MAX_SAMPLES = 2880;

    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final Map<LocalDateTime, Sample> samples = new TreeMap<>();

    /**
     * 구간에 해당하는 표본을 기록한다.
     *
     * <p>같은 15초 구간에 이미 표본이 있으면 중복 저장하지 않고 기존 표본을 반환한다. 상한을
     * 넘으면 가장 오래된 구간부터 버린다.</p>
     */
    public synchronized Sample record(LocalDateTime now, Integer cpuPercent, Integer memoryPercent) {
        LocalDateTime bucketStart = bucketStart(now);
        Sample existing = samples.get(bucketStart);
        if (existing != null) {
            return existing;
        }
        Sample sample = new Sample(bucketStart, cpuPercent, memoryPercent);
        samples.put(bucketStart, sample);
        prune(now);
        return sample;
    }

    /**
     * 조회 시점 기준으로 유효한 표본을 오래된 순서로 반환한다.
     *
     * <p>유효기간을 넘긴 표본은 반환하지 않고 함께 정리한다.</p>
     */
    public synchronized List<Sample> samples(LocalDateTime now) {
        prune(now);
        return new ArrayList<>(samples.values());
    }

    /** 조회 시점 기준으로 유효한 표본 수다. */
    public synchronized int size(LocalDateTime now) {
        prune(now);
        return samples.size();
    }

    /** 화면 차트용 라벨이다. 실제 표본의 구간 시작 시각을 쓴다. */
    public static String label(LocalDateTime bucketStart) {
        return bucketStart.format(LABEL_FORMAT);
    }

    /** 초 단위 구간 시작 시각으로 내린다. 벽시계 기준 자정부터의 초를 잘라 로컬 시각을 유지한다. */
    static LocalDateTime bucketStart(LocalDateTime now) {
        long seconds = SAMPLE_INTERVAL.getSeconds();
        long secondOfDay = now.toLocalTime().toSecondOfDay();
        long truncated = secondOfDay - (secondOfDay % seconds);
        return now.toLocalDate().atStartOfDay().plusSeconds(truncated);
    }

    /** 유효기간 경계는 "조회 시점 - 12시간"을 포함한다. 그보다 이른 표본은 만료다. */
    private void prune(LocalDateTime now) {
        LocalDateTime oldestAllowed = now.minus(RETENTION);
        samples.keySet().removeIf(bucketStart -> bucketStart.isBefore(oldestAllowed));
        while (samples.size() > MAX_SAMPLES) {
            samples.remove(samples.keySet().iterator().next());
        }
    }

    /** 한 번의 수집 결과다. 측정 불가 지표는 {@code null}이다. */
    public record Sample(LocalDateTime bucketStart, Integer cpuPercent, Integer memoryPercent) {
    }
}
