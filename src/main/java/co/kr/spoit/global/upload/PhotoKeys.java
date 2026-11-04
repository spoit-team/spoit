package co.kr.spoit.global.upload;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Pattern;

/** 저장 경로(키) 생성 규칙. 원본 파일 이름은 쓰지 않는다. */
final class PhotoKeys {

    private static final Pattern DIRECTORY = Pattern.compile("[a-z]+");
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private PhotoKeys() {
    }

    /** {@code matches/2026/11/<uuid>.jpg} */
    static String newKey(String directory, UploadedPhoto photo) {
        if (directory == null || !DIRECTORY.matcher(directory).matches()) {
            throw new IllegalArgumentException("저장 폴더 이름이 올바르지 않습니다: " + directory);
        }
        LocalDate today = LocalDate.now(ZONE);
        return String.format("%s/%d/%02d/%s.%s", directory, today.getYear(), today.getMonthValue(),
                UUID.randomUUID(), photo.extension());
    }
}
