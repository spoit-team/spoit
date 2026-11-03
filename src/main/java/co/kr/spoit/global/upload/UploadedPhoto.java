package co.kr.spoit.global.upload;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

/**
 * 형식·크기 검증을 통과한 사진이다.
 *
 * <p>확장자와 Content-Type은 브라우저가 보낸 값이 아니라 파일 앞부분(시그니처)으로 판별한다.
 * JPG·PNG·WEBP만 받는다.</p>
 */
public final class UploadedPhoto {

    /** 사진 한 장 최대 크기 (5MB). 서블릿 업로드 한도는 application.properties에서 더 크게 둔다. */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final byte[] bytes;
    private final String extension;
    private final String contentType;

    private UploadedPhoto(byte[] bytes, String extension, String contentType) {
        this.bytes = bytes;
        this.extension = extension;
        this.contentType = contentType;
    }

    /**
     * 업로드 파일을 검증한다. 파일이 없거나 비어 있으면 {@code null}을 돌려준다.
     *
     * @throws InvalidPhotoException 크기 초과·지원하지 않는 형식·읽기 실패
     */
    public static UploadedPhoto from(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidPhotoException("사진은 5MB 이하로 올려 주세요.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidPhotoException("사진을 읽지 못했습니다. 다시 시도해 주세요.");
        }
        return of(bytes);
    }

    /** 바이트 배열을 검증한다. 비어 있으면 {@code null}. */
    public static UploadedPhoto of(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        if (bytes.length > MAX_BYTES) {
            throw new InvalidPhotoException("사진은 5MB 이하로 올려 주세요.");
        }
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return new UploadedPhoto(bytes, "jpg", "image/jpeg");
        }
        if (startsWith(bytes, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new UploadedPhoto(bytes, "png", "image/png");
        }
        if (startsWith(bytes, 0, 0x52, 0x49, 0x46, 0x46) && startsWith(bytes, 8, 0x57, 0x45, 0x42, 0x50)) {
            return new UploadedPhoto(bytes, "webp", "image/webp");
        }
        throw new InvalidPhotoException("사진은 JPG·PNG·WEBP 파일만 올릴 수 있습니다.");
    }

    private static boolean startsWith(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    public byte[] bytes() {
        return bytes;
    }

    /** jpg·png·webp */
    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    @Override
    public String toString() {
        return "UploadedPhoto[" + contentType + ", " + bytes.length + " bytes]";
    }
}
