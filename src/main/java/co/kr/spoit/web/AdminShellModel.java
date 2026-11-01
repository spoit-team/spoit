package co.kr.spoit.web;

import java.util.Collections;
import java.util.List;

import org.springframework.ui.Model;

/**
 * 관리자 화면들이 공유하는 공통 셸 뷰 속성을 구성하는 정적 유틸리티다.
 *
 * <p>관리자 표시 정보와 알림이 아직 연동되지 않아 셸에는 빈 기본값을 전달한다.
 * 비즈니스 로직이나 DB 접근은 두지 않는다.</p>
 */
public final class AdminShellModel {

    /** 관리자 셸이 공유하는 불변 빈 목록이다. */
    private static final List<Object> EMPTY_LIST = Collections.emptyList();

    private AdminShellModel() {
    }

    /** 관리자 화면 공통 셸 속성을 모델에 추가한다. */
    public static void apply(Model model) {
        model.addAttribute("adminUser", null);
        model.addAttribute("adminNotifications", EMPTY_LIST);
        model.addAttribute("unreadAdminNotificationCount", 0);
        model.addAttribute("adminNotificationSummary", "");
    }
}
