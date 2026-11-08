package co.kr.spoit.global.upload;

/**
 * 업로드한 사진 파일 저장소다.
 *
 * <p>{@code spoit.upload.storage} 값으로 구현을 고른다. 로컬 개발은 {@code local}(기본값, 서버 디스크),
 * 운영 서버는 {@code s3}(AWS S3)를 쓴다. DB에는 {@link #store}가 돌려준 공개 URL을 그대로 저장한다.</p>
 */
public interface PhotoStorage {

    /**
     * 검증을 마친 사진을 저장하고 화면에서 바로 쓸 수 있는 공개 URL을 돌려준다.
     *
     * @param directory 저장 폴더 이름(예: {@code matches}). 영문 소문자만 쓴다.
     */
    String store(String directory, UploadedPhoto photo);

    /**
     * 이 저장소가 만든 URL이면 파일을 지운다. 다른 저장소의 URL이거나 이미 없는 파일이면 아무것도 하지 않는다.
     * 정리 작업이므로 실패해도 예외를 던지지 않는다.
     */
    void delete(String url);
}
