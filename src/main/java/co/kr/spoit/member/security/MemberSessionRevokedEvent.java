package co.kr.spoit.member.security;

/**
 * 회원의 활성 인증 수단(HTTP 세션·알림 웹소켓)을 커밋 이후 폐기했다는 사건이다.
 *
 * <p>{@link MemberSessionRevocationService}가 커밋 이후에 발행한다. HTTP 세션 만료 표시는
 * 그 서비스가 직접 처리하고, 프로세스 밖 연결(알림 STOMP 세션)은 이 사건을 받은 쪽이 닫는다.
 * 이렇게 두면 회원 보안 코드가 알림 웹소켓 구현을 직접 알 필요가 없다.</p>
 *
 * @param userId 폐기 대상 회원 번호
 */
public record MemberSessionRevokedEvent(Long userId) {
}
