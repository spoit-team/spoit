package co.kr.spoit.notification.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.notification.dto.NotificationListResponse;
import co.kr.spoit.notification.service.NotificationCommandService;
import co.kr.spoit.notification.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;

/**
 * 알림 JSON API.
 *
 * <p>모든 경로는 인증과 CSRF 보호를 받는다({@code SecurityConfig}). 조회·변경은 항상 로그인
 * 주체의 회원 번호로만 수행하므로, 다른 사용자의 알림 번호를 지정해도 아무 것도 바뀌지 않는다.
 * 변경 응답에는 개인정보나 다른 사용자의 존재 여부를 담지 않고 결과 상태만 돌려준다.</p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    /** 모든 미확인 알림과 함께 읽을 최근 알림의 수. */
    private static final int LIST_LIMIT = 30;

    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;

    /** 알림 목록과 읽지 않은 수. 로그인하지 않았으면 401. */
    @GetMapping
    public ResponseEntity<NotificationListResponse> list(Authentication authentication) {
        Long userId = userIdOf(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(queryService.findInbox(userId, LIST_LIMIT));
    }

    /** 개별 읽음. 소유하지 않았거나 없는 알림이면 404. */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> read(@PathVariable long notificationId, Authentication authentication) {
        Long userId = userIdOf(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean changed = commandService.markRead(userId, notificationId, LocalDateTime.now());
        return changed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** 개별 삭제. 소유하지 않았거나 없는 알림이면 404. */
    @PostMapping("/{notificationId}/delete")
    public ResponseEntity<Void> delete(@PathVariable long notificationId, Authentication authentication) {
        Long userId = userIdOf(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean deleted = commandService.delete(userId, notificationId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** 모두 읽음. 변경 건수와 무관하게 204를 돌려준다(남은 알림 유무를 흘리지 않는다). */
    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll(Authentication authentication) {
        Long userId = userIdOf(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        commandService.markAllRead(userId, LocalDateTime.now());
        return ResponseEntity.noContent().build();
    }

    /** 로그인 주체의 회원 번호. 익명·비로그인은 null. */
    static Long userIdOf(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getPrincipal() instanceof MemberPrincipal principal
                ? principal.getUserId() : null;
    }
}
