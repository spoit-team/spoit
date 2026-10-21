package co.kr.spoit.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security의 현재 스캐폴드 설정이다.
 *
 * <p>현재 단계에서는 실제 인증/인가가 구현되어 있지 않다. 모든 요청을
 * {@code permitAll()}로 허용하고 폼 로그인과 HTTP Basic을 비활성화하여,
 * 컨트롤러가 인증 없이 페이지를 렌더링할 수 있도록 한 임시 구성이다.
 * 따라서 로그인 화면({@code /login})은 표시되지만 세션 인증이나 권한 검사는
 * 수행되지 않는다.</p>
 *
 * <p>향후 인증을 도입할 때는 URL별 접근 규칙, 로그인/로그아웃 처리,
 * 인증 주체(principal) 조회를 이곳에서 구성하고, POST 기반 업무 요청에 대한
 * CSRF 정책도 함께 명시해야 한다. 현재는 별도 CSRF 설정을 두지 않았으므로
 * Spring Security의 기본 CSRF 보호가 그대로 적용된다. GET 페이지만 제공하는
 * 현 상태에서는 문제가 없지만, 상태를 변경하는 POST 엔드포인트를 추가할 때는
 * 폼에 CSRF 토큰을 포함하거나 의도적으로 예외 처리 여부를 결정해야 한다.</p>
 */
@Configuration
public class SecurityConfig {

    /**
     * 보안 필터 체인을 구성한다.
     *
     * <p>현재 반환하는 체인은 화면 확인용이며, 인증 실패/성공 핸들러,
     * 세션 정책, 예외 처리 등은 아직 정의되어 있지 않다.</p>
     *
     * @param http 스프링 시큐리티가 제공하는 필터 체인 빌더
     * @return 구성이 완료된 필터 체인
     * @throws Exception 필터 체인 구성 중 발생할 수 있는 예외
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 모든 경로를 임시로 공개한다. 실제 권한 규칙은 인증 도입 시 교체한다.
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                // 화면 렌더링만 수행하는 동안 사용하지 않는 인증 방식을 끈다.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
