package co.kr.spoit.match.dto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;

/**
 * 매칭방 목록 무한 스크롤 커서. 목록 정렬(start_at, match_id)의 마지막 행 위치다.
 *
 * <p>화면에는 base64url 문자열로만 나가며, 형식이 틀린 값은 첫 페이지 요청으로 처리한다.</p>
 */
public record MatchCursor(LocalDateTime startAt, long matchId) {

    private static final String SEPARATOR = "_";

    public static MatchCursor of(MatchRow row) {
        return new MatchCursor(row.getStartAt(), row.getMatchId());
    }

    public String encode() {
        String raw = startAt + SEPARATOR + matchId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<MatchCursor> decode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value.strip()), StandardCharsets.UTF_8);
            int at = raw.lastIndexOf(SEPARATOR);
            if (at <= 0) {
                return Optional.empty();
            }
            return Optional.of(new MatchCursor(
                    LocalDateTime.parse(raw.substring(0, at)),
                    Long.parseLong(raw.substring(at + 1))));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
