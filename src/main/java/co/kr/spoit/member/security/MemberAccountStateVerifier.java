package co.kr.spoit.member.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import co.kr.spoit.member.domain.MemberPrincipal;

/**
 * 인증된 {@link MemberPrincipal}이 <strong>지금도</strong> 같은 회원이며 사용 가능한지 확인한다.
 *
 * <p>세션에 저장된 주체는 로그인 시점의 스냅샷이라 역할 변경·탈퇴·이용정지가 반영되지 않는다.
 * 이 유틸리티는 {@link UserDetailsService}로 회원을 다시 읽어 다음을 모두 만족할 때만 통과시킨다.</p>
 *
 * <ul>
 *   <li>같은 회원 번호(userId)다.</li>
 *   <li>현재 계정이 사용 가능하다({@link MemberPrincipal#isEnabled()} — 탈퇴·이용정지 중이 아님).</li>
 *   <li>현재 역할(권한)이 세션 주체의 역할과 같다.</li>
 * </ul>
 *
 * <p>조회 실패·예외는 모두 거부(fail closed)로 처리한다. 이 확인은 인증 완료 시점과 알림 웹소켓
 * 핸드셰이크처럼 <em>연결을 새로 여는 시점</em>에만 쓰며, 요청마다 DB를 다시 읽지는 않는다.</p>
 */
public final class MemberAccountStateVerifier {

    private static final Logger log = LoggerFactory.getLogger(MemberAccountStateVerifier.class);

    private MemberAccountStateVerifier() {
    }

    /**
     * 주체가 아직 같은 회원이고 사용 가능한 상태인지 확인한다.
     *
     * @param authenticated 세션에 저장된(또는 곧 저장될) 인증 주체
     * @param userDetailsService 최신 회원을 읽을 서비스
     * @return 같은 회원이면서 사용 가능할 때만 {@code true}
     */
    public static boolean isStillActive(MemberPrincipal authenticated, UserDetailsService userDetailsService) {
        if (authenticated == null || authenticated.getUserId() == null || userDetailsService == null) {
            return false;
        }
        try {
            UserDetails loaded = userDetailsService.loadUserByUsername(authenticated.getUsername());
            if (!(loaded instanceof MemberPrincipal current)) {
                return false;
            }
            return authenticated.getUserId().equals(current.getUserId())
                    && current.isEnabled()
                    && current.getAuthorities().equals(authenticated.getAuthorities());
        } catch (RuntimeException exception) {
            // 회원이 사라졌거나 조회에 실패하면 인증을 이어갈 근거가 없다. fail closed.
            log.debug("계정 상태 재확인 실패: userId={}", authenticated.getUserId(), exception);
            return false;
        }
    }
}
