package co.kr.spoit.global.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 운영 서버용 사진 저장소. AWS S3 버킷에 올리고 {@code spoit.upload.s3.public-base-url} 아래 URL을 돌려준다.
 *
 * <p>인증은 AWS 기본 자격 증명 체인을 따른다(EC2 인스턴스 역할 또는 {@code AWS_ACCESS_KEY_ID}·
 * {@code AWS_SECRET_ACCESS_KEY} 환경 변수). 객체 ACL은 쓰지 않으므로, 브라우저가 사진을 읽으려면
 * 버킷 정책으로 저장 경로의 공개 읽기를 허용하거나 CloudFront 주소를 {@code public-base-url}로 지정한다.</p>
 */
@Component
@ConditionalOnProperty(name = "spoit.upload.storage", havingValue = "s3")
public class S3PhotoStorage implements PhotoStorage, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(S3PhotoStorage.class);

    private final S3Client client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3PhotoStorage(@Value("${spoit.upload.s3.bucket:}") String bucket,
            @Value("${spoit.upload.s3.region:ap-northeast-2}") String region,
            @Value("${spoit.upload.s3.public-base-url:}") String publicBaseUrl) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("spoit.upload.storage=s3이면 spoit.upload.s3.bucket 값이 필요합니다.");
        }
        this.bucket = bucket.strip();
        String regionName = region.strip();
        this.client = S3Client.builder().region(Region.of(regionName)).build();
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? "https://" + this.bucket + ".s3." + regionName + ".amazonaws.com"
                : publicBaseUrl.strip();
        this.publicBaseUrl = base.endsWith("/") ? base : base + "/";
    }

    @Override
    public String store(String directory, UploadedPhoto photo) {
        String key = PhotoKeys.newKey(directory, photo);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(photo.contentType())
                // 키가 매번 새 UUID라 내용이 바뀌지 않으므로 오래 캐시해도 된다.
                .cacheControl("public, max-age=31536000, immutable")
                .build();
        client.putObject(request, RequestBody.fromBytes(photo.bytes()));
        return publicBaseUrl + key;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(publicBaseUrl)) {
            return;
        }
        String key = url.substring(publicBaseUrl.length());
        if (key.isEmpty()) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException exception) {
            log.warn("S3 사진 삭제 실패: {}", key, exception);
        }
    }

    @Override
    public void destroy() {
        client.close();
    }
}
