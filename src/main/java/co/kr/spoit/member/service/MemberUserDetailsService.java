package co.kr.spoit.member.service;

import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * 스프링 시큐리티 인증 대상 조회 계약.
 * 구현체가 빈으로 등록되면 폼 로그인의 {@code DaoAuthenticationProvider}가 연결된다.
 */
public interface MemberUserDetailsService extends UserDetailsService {
}
