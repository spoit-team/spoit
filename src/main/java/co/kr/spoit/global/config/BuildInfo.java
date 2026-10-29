package co.kr.spoit.global.config;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 정적 리소스 캐시 버스팅용 버전 값을 모든 뷰에 주입한다.
 *
 * <p>템플릿은 CSS 링크에 {@code ?v=}를 붙여 재배포 시 브라우저 캐시를 무력화한다.
 * 값이 매 요청마다 바뀌면 캐시를 전혀 쓰지 못하므로, 서버가 뜬 시점의 값(시작 밀리초)을
 * 고정해 노출한다. 서버 재시작 사이에는 같은 URL이 유지되어 브라우저 캐시가 정상 동작하고,
 * 재배포(재시작)할 때만 새 버전을 받는다.</p>
 */
@ControllerAdvice
public class BuildInfo {

    /** 서버 시작 시각(밀리초). 재시작할 때만 바뀐다. */
    private final long startedAt = System.currentTimeMillis();

    /** 모든 뷰에서 {@code cssVersion}으로 쓸 수 있다. */
    @ModelAttribute("cssVersion")
    public String cssVersion() {
        return Long.toString(startedAt, Character.MAX_RADIX);
    }
}
