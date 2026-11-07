package co.kr.spoit.notification.controller;

import java.time.LocalDateTime;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.notification.service.NotificationCommandService;
import lombok.RequiredArgsConstructor;

/**
 * 서버 폼 기반 알림 읽음·삭제 처리.
 *
 * <p>JavaScript가 없는 브라우저와 관리자 알림 모달의 대체 경로다. 모든 변경은 로그인 주체의
 * 회원 번호로만 수행하고, 응답은 검증된 내부 경로로만 리다이렉트한다. 다른 사용자의 알림
 * 번호를 지정해도 변경 없이 같은 화면으로 돌아간다(존재 여부를 흘리지 않는다).</p>
 */
@Controller
@RequestMapping
@RequiredArgsConstructor
public class NotificationPageController {

    /** 사용자 화면 기본 복귀 경로. */
    private static final String MYPAGE_NOTIFICATIONS = "/mypage#notifications";

    /** 시설 관리자 화면 기본 복귀 경로. */
    private static final String FACILITY_ADMIN = "/admin/facility";

    /** 시스템 관리자 화면 기본 복귀 경로. */
    private static final String SYSTEM_ADMIN = "/admin/system";

    /** 관리자 역할 라벨과 기본 복귀 경로 쌍의 판별에 쓰는 값. */
    private static final String SYSTEM_ADMIN_ROLE_LABEL = "시스템 관리자";

    private final NotificationCommandService commandService;

    /** 마이페이지 알림 개별 읽음. */
    @PostMapping("/mypage/notifications/{notificationId}/read")
    public String readMyPage(@PathVariable long notificationId, Authentication authentication,
            @RequestParam(name = "returnTo", required = false) String returnTo) {
        Long userId = userIdOf(authentication);
        if (userId == null) {
            return "redirect:/login";
        }
        commandService.markRead(userId, notificationId, LocalDateTime.now());
        return redirectTo(returnTo, MYPAGE_NOTIFICATIONS);
    }

    /** 마이페이지 알림 개별 삭제. */
    @PostMapping("/mypage/notifications/{notificationId}/delete")
    public String deleteMyPage(@PathVariable long notificationId, Authentication authentication,
            @RequestParam(name = "returnTo", required = false) String returnTo) {
        Long userId = userIdOf(authentication);
        if (userId == null) {
            return "redirect:/login";
        }
        commandService.delete(userId, notificationId);
        return redirectTo(returnTo, MYPAGE_NOTIFICATIONS);
    }

    /** 마이페이지 알림 모두 읽음. */
    @PostMapping("/mypage/notifications/read-all")
    public String readAllMyPage(Authentication authentication,
            @RequestParam(name = "returnTo", required = false) String returnTo) {
        Long userId = userIdOf(authentication);
        if (userId == null) {
            return "redirect:/login";
        }
        commandService.markAllRead(userId, LocalDateTime.now());
        return redirectTo(returnTo, MYPAGE_NOTIFICATIONS);
    }

    /** 관리자 알림 개별 읽음. 시설·시스템 관리자 화면 모두에서 쓴다. */
    @PostMapping("/admin/notifications/{notificationId}/read")
    public String readAdmin(@PathVariable long notificationId, Authentication authentication,
            @RequestParam(name = "returnTo", required = false) String returnTo) {
        Long userId = userIdOf(authentication);
        if (userId == null) {
            return "redirect:/login";
        }
        commandService.markRead(userId, notificationId, LocalDateTime.now());
        return redirectTo(returnTo, adminHome(authentication));
    }

    /** 관리자 알림 개별 삭제. */
    @PostMapping("/admin/notifications/{notificationId}/delete")
    public String deleteAdmin(@PathVariable long notificationId, Authentication authentication,
            @RequestParam(name = "returnTo", required = false) String returnTo) {
        Long userId = userIdOf(authentication);
        if (userId == null) {
            return "redirect:/login";
        }
        commandService.delete(userId, notificationId);
        return redirectTo(returnTo, adminHome(authentication));
    }

    /** 관리자 알림 모두 읽음. */
    @PostMapping("/admin/notifications/read-all")
    public String readAllAdmin(Authentication authentication,
            @RequestParam(name = "returnTo", required = false) String returnTo) {
        Long userId = userIdOf(authentication);
        if (userId == null) {
            return "redirect:/login";
        }
        commandService.markAllRead(userId, LocalDateTime.now());
        return redirectTo(returnTo, adminHome(authentication));
    }

    /**
     * 복귀 경로를 검증한다. 같은 출처의 절대 경로만 허용하고, 그 밖의 값은 기본 경로로 대체한다.
     * {@code //host}, 역슬래시, 제어문자가 섞인 값은 외부로 나가는 링크가 될 수 있어 버린다.
     */
    static String redirectTo(String requested, String fallback) {
        if (requested == null) {
            return "redirect:" + fallback;
        }
        String trimmed = requested.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            return "redirect:" + fallback;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char current = trimmed.charAt(i);
            if (current == '\\' || current < 0x20 || current == 0x7f) {
                return "redirect:" + fallback;
            }
        }
        return "redirect:" + trimmed;
    }

    /** 관리자 역할에 맞는 기본 복귀 경로. 시설 관리자는 시설 화면으로 돌아간다. */
    private static String adminHome(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof MemberPrincipal principal
                && SYSTEM_ADMIN_ROLE_LABEL.equals(principal.getRoleLabel())) {
            return SYSTEM_ADMIN;
        }
        return FACILITY_ADMIN;
    }

    /** 로그인 주체의 회원 번호. 익명·비로그인은 null. */
    private static Long userIdOf(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getPrincipal() instanceof MemberPrincipal principal
                ? principal.getUserId() : null;
    }
}
