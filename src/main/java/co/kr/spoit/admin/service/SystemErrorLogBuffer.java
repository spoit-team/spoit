package co.kr.spoit.admin.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import co.kr.spoit.admin.dto.SystemErrorRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 현재 프로세스에서 발생한 {@code WARN}·{@code ERROR} 로그를 메모리에만 보관한다.
 *
 * <p>사용자가 선택한 방식대로 최근 {@value #CAPACITY}건만 남기고 재시작하면 초기화된다. 파일·DB에
 * 쓰지 않으므로 스키마·마이그레이션·{@code build.gradle} 변경이 필요 없다. logback 루트 로거에
 * 이 클래스 자신을 appender로 붙여 기존 런타임 logging API({@code log.error}, {@code log.warn})가
 * 남기는 기록을 그대로 포착한다. 루트 로거에 붙이므로 로거 이름은 화면 표시용 서비스 이름으로
 * 축약한다.</p>
 *
 * <p>화면으로 나가는 값은 저장 전에 민감값 마스킹과 길이 제한을 거친다. 이메일·전화번호·비밀번호·
 * 토큰류 값은 마스킹되고, 요약은 {@value #SUMMARY_MAX}자, 상세는 {@value #DETAIL_MAX}자로 자른다.
 * 버퍼가 가득 차면 가장 오래된 기록을 버리므로 메모리 사용량이 늘지 않는다.</p>
 */
@Component
public class SystemErrorLogBuffer extends AppenderBase<ILoggingEvent> {

    /** 보관 건수 상한. 재시작하면 비워진다. */
    static final int CAPACITY = 500;

    static final int SUMMARY_MAX = 200;
    static final int DETAIL_MAX = 600;

    /** 버퍼가 받아들이는 최소 레벨. 화면 계약(오류/경고)과 수집 범위를 맞춘다. */
    private static final Level MINIMUM_LEVEL = Level.WARN;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MASK = "***";
    private static final String UNKNOWN_SERVICE = "app";

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("\\b0\\d{1,2}[-. ]?\\d{3,4}[-. ]?\\d{4}\\b");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|token|secret|authorization|api[-_]?key|access[-_]?key)\\b\\s*[=:]\\s*\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._-]+");

    private final Deque<SystemErrorRecord> records = new ArrayDeque<>(CAPACITY);
    private Logger attachedRoot;

    /** logback 루트 로거에 스스로를 붙이고 시작한다. */
    @PostConstruct
    public void attach() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            return;
        }
        setName("system-error-buffer");
        setContext(context);
        start();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(this);
        attachedRoot = root;
    }

    /** 애플리케이션 종료 시 루트 로거에서 분리한다. */
    @PreDestroy
    public void detach() {
        if (attachedRoot != null) {
            attachedRoot.detachAppender(this);
        }
        attachedRoot = null;
        stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null || event.getLevel() == null || !event.getLevel().isGreaterOrEqual(MINIMUM_LEVEL)) {
            return;
        }
        SystemErrorRecord record = toRecord(event);
        synchronized (records) {
            if (records.size() >= CAPACITY) {
                records.pollFirst();
            }
            records.addLast(record);
        }
    }

    /** 보관 중인 최신 기록부터 돌려준다. 반환 목록은 복사본이라 호출자가 바꿔도 버퍼에 영향이 없다. */
    public List<SystemErrorRecord> recent() {
        synchronized (records) {
            List<SystemErrorRecord> copy = new ArrayList<>(records.size());
            var iterator = records.descendingIterator();
            while (iterator.hasNext()) {
                copy.add(iterator.next());
            }
            return copy;
        }
    }

    /** 지정 시각 이후에 발생한 기록만 최신 순으로 돌려준다. */
    public List<SystemErrorRecord> recentSince(LocalDateTime since) {
        List<SystemErrorRecord> all = recent();
        if (since == null) {
            return all;
        }
        List<SystemErrorRecord> filtered = new ArrayList<>();
        for (SystemErrorRecord record : all) {
            if (!record.time().isBefore(since)) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    /** 버퍼를 비운다. 테스트에서 수집 상태를 초기화할 때만 쓴다. */
    void clear() {
        synchronized (records) {
            records.clear();
        }
    }

    /** 테스트에서 시각을 고정한 기록을 넣을 때만 쓴다. 실제 수집은 logback appender 경로를 쓴다. */
    void recordForTest(SystemErrorRecord record) {
        if (record == null) {
            return;
        }
        synchronized (records) {
            if (records.size() >= CAPACITY) {
                records.pollFirst();
            }
            records.addLast(record);
        }
    }

    /** 테스트에서 시각을 고정하지 않고도 수집 규칙을 확인할 수 있도록 변환만 분리했다. */
    static SystemErrorRecord toRecord(ILoggingEvent event) {        Level level = event.getLevel();
        String summary = truncate(mask(message(event)), SUMMARY_MAX);
        String throwableDetail = throwableDetail(event.getThrowableProxy());
        String detail = throwableDetail == null
                ? truncate(mask(message(event)), DETAIL_MAX)
                : truncate(mask(throwableDetail), DETAIL_MAX);
        return new SystemErrorRecord(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZONE),
                level.toString(),
                event.getLoggerName(),
                serviceOf(event.getLoggerName()),
                codeOf(event.getThrowableProxy()),
                summary,
                detail);
    }

    /** 화면 표시용 서비스 이름. 도메인 패키지의 두 번째 조각을 쓰고, 앱 패키지 밖이면 {@code app}. */
    static String serviceOf(String loggerName) {
        if (loggerName == null || loggerName.isBlank()) {
            return UNKNOWN_SERVICE;
        }
        String[] parts = loggerName.split("\\.");
        if (parts.length < 4 || !"co".equals(parts[0]) || !"kr".equals(parts[1])
                || !"spoit".equals(parts[2])) {
            return UNKNOWN_SERVICE;
        }
        return parts[3];
    }

    static String codeOf(IThrowableProxy throwable) {
        return throwable == null ? "LOG" : throwable.getClassName();
    }

    static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String masked = EMAIL.matcher(value).replaceAll(MASK);
        masked = PHONE.matcher(masked).replaceAll(MASK);
        masked = SECRET_ASSIGNMENT.matcher(masked).replaceAll("$1=" + MASK);
        masked = BEARER.matcher(masked).replaceAll("Bearer " + MASK);
        return masked;
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    static String timeText(LocalDateTime time) {
        return time == null ? "—" : TIME_FORMAT.format(time);
    }

    private static String message(ILoggingEvent event) {
        String formatted = event.getFormattedMessage();
        return formatted == null ? "" : formatted.strip();
    }

    /** 예외가 있으면 원인 사슬을 합쳐 상세에 남긴다(스택 프레임 수는 제한한다). */
    private static String throwableDetail(IThrowableProxy proxy) {
        if (proxy == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        IThrowableProxy current = proxy;
        int depth = 0;
        while (current != null && depth < 5) {
            if (depth > 0) {
                builder.append(" ← ");
            }
            builder.append(current.getClassName());
            if (current.getMessage() != null) {
                builder.append(": ").append(current.getMessage());
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    /** 필터 비교용 소문자. */
    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
