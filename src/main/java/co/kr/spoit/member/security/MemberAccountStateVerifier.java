package co.kr.spoit.member.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

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

    /** 세션 주체와 최신 계정 상태를 비교한 결과. */
    public enum VerificationResult {
        ACTIVE,
        REJECTED,
        UNAVAILABLE
    }

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
        return verify(authenticated, userDetailsService, false) == VerificationResult.ACTIVE;
    }

    /**
     * 세션 인증 주체와 최신 계정 상태를 비교한다.
     *
     * <p>계정이 없거나 상태·역할이 다르면 {@link VerificationResult#REJECTED}, 그 밖의 조회 예외는
     * {@link VerificationResult#UNAVAILABLE}로 구분한다. 두 결과 모두 인증은 거부되며,
     * 웹소켓 핸드셰이크처럼 일시적 조회 장애에 재시도 가능한 응답을 줄 호출자만 결과를 구분한다.</p>
     *
     * @return 계정 상태가 같으면 {@link VerificationResult#ACTIVE}, 거부 사유가 있으면
     *         {@link VerificationResult#REJECTED}, 조회 예외면 {@link VerificationResult#UNAVAILABLE}
     */
    public static VerificationResult verify(MemberPrincipal authenticated,
            UserDetailsService userDetailsService) {
        return verify(authenticated, userDetailsService, true);
    }

    private static VerificationResult verify(MemberPrincipal authenticated,
            UserDetailsService userDetailsService, boolean logUnavailableAsWarning) {
        if (authenticated == null || authenticated.getUserId() == null || userDetailsService == null) {
            return VerificationResult.REJECTED;
        }
        try {
            UserDetails loaded = userDetailsService.loadUserByUsername(authenticated.getUsername());
            if (!(loaded instanceof MemberPrincipal current)) {
                return VerificationResult.REJECTED;
            }
            boolean active = authenticated.getUserId().equals(current.getUserId())
                    && current.isEnabled()
                    && current.getAuthorities().equals(authenticated.getAuthorities());
            if (active) {
                return VerificationResult.ACTIVE;
            }
            return VerificationResult.REJECTED;
        } catch (UsernameNotFoundException exception) {
            // 계정이 없거나 탈퇴·비밀번호 미설정 상태는 재시도해도 해결되지 않는 거부 사유다.
            log.debug("계정 상태 재확인 실패: userId={}", authenticated.getUserId(), exception);
            return VerificationResult.REJECTED;
        } catch (RuntimeException exception) {
            // 조회 중 예외는 연결 근거가 아니므로 거부하되, 일시적 장애와 권한 거부를 구분한다.
            if (logUnavailableAsWarning) {
                log.warn("계정 상태 조회를 완료할 수 없음: userId={}", authenticated.getUserId(), exception);
            } else {
                log.debug("계정 상태 재확인 실패: userId={}", authenticated.getUserId(), exception);
            }
            return VerificationResult.UNAVAILABLE;
        }
    }
}
