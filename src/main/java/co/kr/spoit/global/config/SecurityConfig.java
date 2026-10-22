package co.kr.spoit.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import lombok.RequiredArgsConstructor;

/**
 * 폼 로그인 기반 인증 설정으로, member 패키지의 인증·핸들러를 필터 체인에 연결만 한다.
 * 모든 화면 라우트는 공개 상태를 유지해야 하며, 보호가 필요한 경로는 {@code authorizeHttpRequests}에 추가한다.
 * 로그아웃은 CSRF 보호를 위해 {@code POST /logout}만 허용하고, remember-me 키는 운영에서
 * {@code spoit.security.remember-me-key}로 반드시 외부 주입해 토큰 위조를 막는다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final MemberLoginSuccessHandler loginSuccessHandler;
    private final MemberLoginFailureHandler loginFailureHandler;

    /** remember-me 토큰 서명 키. 운영에서는 외부 설정으로 덮어쓴다. */
    @Value("${spoit.security.remember-me-key:spoit-remember-me-dev-key}")
    private String rememberMeKey;

    /**
     * 비밀번호 검증기를 등록한다.
     * {@code app_user.password_hash}에 BCrypt 해시가 저장돼 있다는 전제이며,
     * 다른 알고리즘 데이터가 섞이면 {@code DelegatingPasswordEncoder}로 교체해야 한다.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 보안 필터 체인을 구성한다. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        // 시큐리티가 처리하는 POST 경로이며 컨트롤러 핸들러는 없다.
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                .rememberMe(remember -> remember
                        .rememberMeParameter("remember")
                        .key(rememberMeKey)
                        .tokenValiditySeconds(60 * 60 * 24 * 14))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?message="
                                + java.net.URLEncoder.encode("로그아웃되었습니다.",
                                        java.nio.charset.StandardCharsets.UTF_8))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "remember-me")
                        .permitAll())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
