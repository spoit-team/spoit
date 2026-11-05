package co.kr.spoit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.ui.ExtendedModelMap;

import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.domain.MemberPrincipalFixture;

/**
 * 공통 모델 어드바이스가 알림·관리자 셸 속성을 로그인 주체 기준으로만 채우는지 검증한다.
 *
 * <p>특히 관리자 셸 템플릿이 참조하는 {@code adminUser.facilityName} 키가 항상 존재해야
 * SpEL 평가가 실패하지 않는다.</p>
 */
class GlobalModelAdviceTest {

    private static final long USER_ID = 7L;

    private final NotificationQueryService queryService = mock(NotificationQueryService.class);
    private final GlobalModelAdvice advice = new GlobalModelAdvice(providerOf(queryService));

    @Test
    @DisplayName("로그인 회원의 알림 목록·읽지 않은 수를 그 회원 번호로만 조회한다")
    void fillsNotificationsForLoggedInUser() {
        given(queryService.findForView(USER_ID, 20)).willReturn(List.of());
        given(queryService.countUnread(USER_ID)).willReturn(3);
        ExtendedModelMap model = new ExtendedModelMap();

        advice.addCommonViewAttributes(model, new MockHttpServletRequest(), authentication(MemberPrincipalFixture.member()));

        assertThat(model.get("unreadCount")).isEqualTo(3);
        assertThat(model.get("notifications")).isEqualTo(List.of());
        verify(queryService).findForView(USER_ID, 20);
    }

    @Test
    @DisplayName("비로그인은 알림을 조회하지 않고 0과 빈 목록을 쓴다")
    void doesNotQueryForAnonymous() {
        ExtendedModelMap model = new ExtendedModelMap();

        advice.addCommonViewAttributes(model, new MockHttpServletRequest(), anonymous());

        assertThat(model.get("unreadCount")).isEqualTo(0);
        assertThat((List<?>) model.get("notifications")).isEmpty();
        verify(queryService, never()).findForView(USER_ID, 20);
        verify(queryService, never()).countUnread(USER_ID);
    }

    @Test
    @DisplayName("알림 조회가 실패해도 화면 모델은 0과 빈 목록으로 유지된다")
    void survivesQueryFailure() {
        given(queryService.countUnread(USER_ID)).willThrow(new IllegalStateException("조회 실패"));
        given(queryService.findForView(USER_ID, 20)).willThrow(new IllegalStateException("조회 실패"));
        ExtendedModelMap model = new ExtendedModelMap();

        advice.addCommonViewAttributes(model, new MockHttpServletRequest(), authentication(MemberPrincipalFixture.member()));

        assertThat(model.get("unreadCount")).isEqualTo(0);
        assertThat((List<?>) model.get("notifications")).isEmpty();
    }

    @Test
    @DisplayName("시설 관리자 셸 속성에는 템플릿이 참조하는 facilityName 키가 포함된다")
    void adminModelContainsEveryTemplateKey() {
        given(queryService.findForView(USER_ID, 20)).willReturn(List.of());
        given(queryService.findForView(USER_ID, 10)).willReturn(List.of());
        given(queryService.countUnread(USER_ID)).willReturn(1);
        ExtendedModelMap model = new ExtendedModelMap();

        advice.addCommonViewAttributes(model, new MockHttpServletRequest(),
                authentication(MemberPrincipalFixture.facilityManager()));

        @SuppressWarnings("unchecked")
        Map<String, Object> adminUser = (Map<String, Object>) model.get("adminUser");
        // admin.html이 참조하는 키들. 하나라도 없으면 템플릿 렌더링이 실패한다.
        assertThat(adminUser).containsKeys("facilityName", "initial", "nickname", "role", "roleLabel");
        assertThat(model.get("adminNotifications")).isEqualTo(List.of());
        assertThat(model.get("unreadAdminNotificationCount")).isEqualTo(1);
        assertThat(model.get("adminNotificationSummary")).isEqualTo("알림 0건 · 미확인 1건");
    }

    @Test
    @DisplayName("일반 회원에게는 관리자 셸 속성을 넣지 않는다")
    void doesNotExposeAdminAttributesToMember() {
        given(queryService.findForView(USER_ID, 20)).willReturn(List.of());
        given(queryService.countUnread(USER_ID)).willReturn(0);
        ExtendedModelMap model = new ExtendedModelMap();

        advice.addCommonViewAttributes(model, new MockHttpServletRequest(), authentication(MemberPrincipalFixture.member()));

        assertThat(model.containsAttribute("adminUser")).isFalse();
        assertThat(model.containsAttribute("adminNotifications")).isFalse();
    }

    @Test
    @DisplayName("조회 서비스 빈이 없으면 알림은 빈 값으로 유지된다")
    void worksWithoutQueryServiceBean() {
        GlobalModelAdvice withoutService = new GlobalModelAdvice(providerOf(null));
        ExtendedModelMap model = new ExtendedModelMap();

        withoutService.addCommonViewAttributes(model, new MockHttpServletRequest(),
                authentication(MemberPrincipalFixture.member()));

        assertThat(model.get("unreadCount")).isEqualTo(0);
        assertThat((List<?>) model.get("notifications")).isEmpty();
    }

    /** 값이 있을 때만 꺼내는 실제 ObjectProvider 동작을 흉내 낸다. */
    private static org.springframework.beans.factory.ObjectProvider<NotificationQueryService> providerOf(
            NotificationQueryService service) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (service != null) {
            beanFactory.registerSingleton("notificationQueryService", service);
        }
        return beanFactory.getBeanProvider(NotificationQueryService.class);
    }

    private static Authentication authentication(MemberPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static Authentication anonymous() {
        return new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }
}
