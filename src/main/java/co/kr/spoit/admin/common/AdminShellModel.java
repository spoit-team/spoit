package co.kr.spoit.admin.common;

import java.util.Collections;
import java.util.List;

import org.springframework.ui.Model;

/**
 * 관리자 화면들이 공유하는 공통 셸 뷰 속성을 구성하는 정적 유틸리티다.
 *
 * <p>알림·관리자 표시 정보는 {@code GlobalModelAdvice}가 로그인 주체 기준으로 이미 채운다.
 * 여기서는 그 값을 덮어쓰지 않고, 어드바이스가 닿지 않는 환경(단위 테스트 등)에서만
 * 빈 기본값을 넣는다. 비즈니스 로직이나 DB 접근은 두지 않는다.</p>
 */
public final class AdminShellModel {

    /** 관리자 셸이 공유하는 불변 빈 목록이다. */
    private static final List<Object> EMPTY_LIST = Collections.emptyList();

    private AdminShellModel() {
    }

    /** 관리자 화면 공통 셸 속성을 모델에 추가한다. 이미 있는 값은 그대로 둔다. */
    public static void apply(Model model) {
        addIfAbsent(model, "adminUser", null);
        addIfAbsent(model, "adminNotifications", EMPTY_LIST);
        addIfAbsent(model, "unreadAdminNotificationCount", 0);
        addIfAbsent(model, "adminNotificationSummary", "");
    }

    private static void addIfAbsent(Model model, String name, Object value) {
        if (!model.containsAttribute(name)) {
            model.addAttribute(name, value);
        }
    }
}
