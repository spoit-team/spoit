package co.kr.spoit.notification.websocket;

import java.security.Principal;

/**
 * STOMP 세션의 사용자 주체. 이름을 회원 번호 문자열로 두어
 * {@code /user/{userId}/queue/notifications} 전송 대상과 구독 세션을 회원 번호로 맞춘다.
 */
record NotificationPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
