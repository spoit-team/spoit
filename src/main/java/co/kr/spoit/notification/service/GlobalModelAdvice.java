package co.kr.spoit.notification.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.dto.CurrentMemberDto;
import co.kr.spoit.notification.dto.NotificationItemDto;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 화면을 렌더링하는 컨트롤러 응답에 공통 뷰 속성을 추가한다.
 *
 * <p>로그인 세션은 템플릿이 프로퍼티를 바로 참조할 수 있도록 {@link CurrentMemberDto}로 변환해
 * {@code currentMember}에 넣고, 비로그인·익명 인증은 {@code null}로 둔다. 알림 관련 속성은
 * 로그인 주체의 회원 번호로만 조회하며, 비로그인은 빈 목록과 0으로 채운다.</p>
 *
 * <p>{@link NotificationQueryService}는 구현 빈이 없는 슬라이스 테스트에서도 동작해야 하므로
 * {@link ObjectProvider}로 선택적으로 주입한다. 없으면 읽지 않은 수는 0, 목록은 비어 있다.</p>
 */
@ControllerAdvice
public class GlobalModelAdvice {

    /** 관리자 화면에 미확인 알림과 함께 표시할 최근 알림의 수. */
    private static final int ADMIN_NOTIFICATION_LIMIT = 10;

    /** 마이페이지에 미확인 알림과 함께 표시할 최근 알림의 수. */
    private static final int MYPAGE_NOTIFICATION_LIMIT = 20;

    /** 시스템 관리자 역할 라벨. */
    private static final String ADMIN_ROLE_LABEL = "시스템 관리자";

    /** 시설 관리자 역할 라벨. */
    private static final String FACILITY_ROLE_LABEL = "시설 관리자";

    private final ObjectProvider<NotificationQueryService> notificationQueryService;

    public GlobalModelAdvice(ObjectProvider<NotificationQueryService> notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    /**
     * 공통 모델 속성을 채운다. 핸들러가 같은 이름을 추가하면 그 값이 최종 모델을 대체한다.
     *
     * <p>관리자 셸 속성({@code adminUser}·{@code adminNotifications}·{@code unreadAdminNotificationCount}·
     * {@code adminNotificationSummary})도 여기서 실제 값으로 채운다. 레이아웃이 관리자 전용 속성을
     * 참조하므로, 관리자 역할이 아니면 값을 넣지 않아 다른 사용자 화면에 개인정보가 섞이지 않는다.</p>
     */
    @ModelAttribute
    public void addCommonViewAttributes(Model model, HttpServletRequest request,
            Authentication authentication) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod method
                && (method.hasMethodAnnotation(ResponseBody.class)
                    || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), ResponseBody.class))) {
            return;
        }
        CurrentMemberDto member = currentMember(authentication);
        model.addAttribute("currentMember", member);
        Long userId = member == null ? null : member.getUserId();
        NotificationQueryService queryService = notificationQueryService.getIfAvailable();

        int unreadCount = unreadCount(queryService, userId);
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("notifications", notifications(queryService, userId, MYPAGE_NOTIFICATION_LIMIT));

        if (isAdminRole(member)) {
            List<NotificationItemDto> adminNotifications =
                    notifications(queryService, userId, ADMIN_NOTIFICATION_LIMIT);
            model.addAttribute("adminUser", adminUser(member));
            model.addAttribute("adminNotifications", adminNotifications);
            model.addAttribute("unreadAdminNotificationCount", unreadCount);
            model.addAttribute("adminNotificationSummary",
                    "알림 " + adminNotifications.size() + "건 · 미확인 " + unreadCount + "건");
        }

        // 응답 버퍼가 커밋된 뒤에는 세션을 만들 수 없으므로 렌더링 전에 CSRF 토큰을 초기화한다.
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
    }

    private static int unreadCount(NotificationQueryService queryService, Long userId) {
        if (queryService == null || userId == null || userId <= 0) {
            return 0;
        }
        try {
            return Math.max(queryService.countUnread(userId), 0);
        } catch (RuntimeException exception) {
            // 알림 조회 실패가 화면 전체를 막지 않게 배지만 0으로 둔다.
            return 0;
        }
    }

    private static List<NotificationItemDto> notifications(NotificationQueryService queryService, Long userId,
            int limit) {
        if (queryService == null || userId == null || userId <= 0) {
            return List.of();
        }
        try {
            return queryService.findForView(userId, limit);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /** 관리자 셸이 참조하는 표시값만 담는다. 템플릿 키 이름은 기존과 같게 유지한다. */
    private static Map<String, Object> adminUser(CurrentMemberDto member) {
        Map<String, Object> admin = new LinkedHashMap<>();
        admin.put("userId", member.getUserId());
        admin.put("nickname", member.getNickname());
        admin.put("initial", member.getInitial());
        admin.put("role", member.getRoleLabel());
        admin.put("roleLabel", member.getRoleLabel());
        // 셸 템플릿이 facilityName을 참조한다. 값이 없어도 키는 있어야 SpEL이 실패하지 않고
        // 템플릿의 기본 문구('담당 시설')로 대체된다.
        admin.put("facilityName", null);
        return admin;
    }

    private static boolean isAdminRole(CurrentMemberDto member) {
        if (member == null) {
            return false;
        }
        String label = member.getRoleLabel();
        return ADMIN_ROLE_LABEL.equals(label) || FACILITY_ROLE_LABEL.equals(label);
    }

    /** 인증 정보에서 화면용 회원 DTO를 만든다. 비로그인·익명 인증이면 {@code null}. */
    private static CurrentMemberDto currentMember(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getPrincipal() instanceof MemberPrincipal principal
                ? CurrentMemberDto.from(principal)
                : null;
    }
}
