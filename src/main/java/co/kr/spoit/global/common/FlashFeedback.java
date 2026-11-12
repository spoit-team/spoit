package co.kr.spoit.global.common;

import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 처리 결과를 화면의 공용 안내(fragments/feedback :: flash → ui-feedback.js)로 넘긴다.
 *
 * <ul>
 *   <li>성공: {@code message} → 오른쪽 아래 토스트</li>
 *   <li>실패: {@code errorTitle} + {@code error} → 가운데 안내 모달(제목 + 원인)</li>
 * </ul>
 *
 * <p>리다이렉트에는 flash 속성만 쓴다. URL 쿼리로 넘기면 새로고침할 때마다 안내가 다시 뜬다.</p>
 */
public final class FlashFeedback {

    public static final String MESSAGE = "message";
    public static final String ERROR = "error";
    public static final String ERROR_TITLE = "errorTitle";

    /** 원인 문구가 비어 있을 때 보여 줄 기본 안내 */
    public static final String FALLBACK_REASON = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";

    private FlashFeedback() {
    }

    public static void success(RedirectAttributes redirect, String message) {
        redirect.addFlashAttribute(MESSAGE, message);
    }

    public static void failure(RedirectAttributes redirect, String title, String reason) {
        redirect.addFlashAttribute(ERROR_TITLE, title);
        redirect.addFlashAttribute(ERROR, reasonOrFallback(reason));
    }

    /** 리다이렉트 없이 같은 요청에서 화면을 다시 그릴 때(입력값 유지) 쓴다. */
    public static void failure(Model model, String title, String reason) {
        model.addAttribute(ERROR_TITLE, title);
        model.addAttribute(ERROR, reasonOrFallback(reason));
    }

    private static String reasonOrFallback(String reason) {
        return reason == null || reason.isBlank() ? FALLBACK_REASON : reason;
    }
}
