package co.kr.spoit.admin.dto;

import java.time.LocalDateTime;

/**
 * 로그 appender가 메모리 버퍼에 남기는 원본 오류 기록이다.
 *
 * <p>화면 모델({@link SystemErrorLog})과 달리 집계·표시 변환이 없다. 메시지·상세는 버퍼에 넣기
 * 전에 이미 길이 제한과 민감값 마스킹을 거친 값이다.</p>
 *
 * @param time       발생 시각
 * @param level      {@code ERROR} 또는 {@code WARN}
 * @param loggerName 기록한 로거 이름
 * @param service    화면에 표시할 서비스(도메인) 이름
 * @param code       예외 클래스 이름, 예외가 없으면 로그 레벨 코드
 * @param summary    한 줄 요약(마스킹·길이 제한 적용)
 * @param detail     상세(마스킹·길이 제한 적용)
 */
public record SystemErrorRecord(LocalDateTime time, String level, String loggerName, String service,
        String code, String summary, String detail) {
}
