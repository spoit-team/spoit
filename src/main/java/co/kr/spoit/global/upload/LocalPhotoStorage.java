package co.kr.spoit.global.upload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 개발용 사진 저장소. 서버 디스크({@code spoit.upload.local-dir}, 기본 {@code ./uploads})에 저장하고
 * {@code /uploads/**} 경로로 제공한다.
 *
 * <p>컨테이너 디스크는 배포 때 초기화되므로 운영 서버에서는 {@code spoit.upload.storage=s3}를 쓴다.</p>
 */
@Component
@ConditionalOnProperty(name = "spoit.upload.storage", havingValue = "local", matchIfMissing = true)
public class LocalPhotoStorage implements PhotoStorage, WebMvcConfigurer {

    static final String URL_PREFIX = "/uploads/";

    private static final Logger log = LoggerFactory.getLogger(LocalPhotoStorage.class);

    private final Path root;

    public LocalPhotoStorage(@Value("${spoit.upload.local-dir:uploads}") String localDir) {
        this.root = Paths.get(localDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(String directory, UploadedPhoto photo) {
        String key = PhotoKeys.newKey(directory, photo);
        Path target = root.resolve(key).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, photo.bytes());
        } catch (IOException exception) {
            throw new UncheckedIOException("사진을 저장하지 못했습니다.", exception);
        }
        return URL_PREFIX + key;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        Path target = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.warn("업로드 사진 삭제 실패: {}", target, exception);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = root.toUri().toString();
        registry.addResourceHandler(URL_PREFIX + "**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
