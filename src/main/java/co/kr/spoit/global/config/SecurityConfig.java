package co.kr.spoit.global.config;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 폼 로그인 기반 인증 설정으로, member 패키지의 인증·핸들러를 필터 체인에 연결한다.
 * 마이페이지 계열({@code /mypage}, {@code /mypage/**})은 로그인을 요구하고,
 * 관리자 화면은 역할별로 분리한다. 시설 관리 화면({@code /admin/facility},
 * {@code /admin/operations})은 {@code ROLE_FACILITY_MANAGER}, 시스템 관리 화면
 * ({@code /admin/system})은 {@code ROLE_ADMIN}만 접근할 수 있으며 서로 교차 접근은
 * 허용하지 않는다. 나머지 화면 라우트는 공개 상태를 유지한다. 보호가 필요한 경로는
 * {@code authorizeHttpRequests}에 추가한다.
 * 로그아웃은 CSRF 보호를 위해 {@code POST /logout}만 허용한다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final MemberLoginSuccessHandler loginSuccessHandler;
    private final MemberLoginFailureHandler loginFailureHandler;

    /** remember-me 서명 키. 소스에 기본값을 두지 않고 외부 주입만 받는다. */
    @Value("${spoit.security.remember-me-key:}")
    private String configuredRememberMeKey;

    /**
     * 비밀번호 검증기를 등록한다.
     * {@code app_user.password_hash}에 BCrypt 해시가 저장돼 있다는 전제이며,
     * 다른 알고리즘 데이터가 섞이면 {@code DelegatingPasswordEncoder}로 교체해야 한다.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * remember-me 서명 키를 결정한다. 주입값이 있으면 그대로 쓰고, 없으면 기동마다
     * 임의 키를 만든다. 소스에 키를 남기지 않으려는 선택이며, 그 대가로 키를 주입하지
     * 않은 환경에서는 재시작할 때 기존 로그인 유지 쿠키가 모두 무효가 된다.
     */
    @Bean
    String rememberMeKey() {
        if (configuredRememberMeKey != null && !configuredRememberMeKey.isBlank()) {
            return configuredRememberMeKey;
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        log.warn("spoit.security.remember-me-key 미설정 — 임시 키로 동작합니다. "
                + "재시작 시 로그인 유지 쿠키가 무효가 되며, 운영에서는 반드시 키를 주입해야 합니다.");
        return Base64.getEncoder().encodeToString(random);
    }

    /** 보안 필터 체인을 구성한다. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        // 시설 관리자 전용 화면: 시스템 관리자도 접근하지 못한다.
                        .requestMatchers("/admin/facility", "/admin/facility/**",
                                "/admin/facilities", "/admin/facilities/**",
                                "/admin/operations", "/admin/operations/**")
                        .hasRole("FACILITY_MANAGER")
                        // 시스템 관리자 전용 화면: 시설 관리자도 접근하지 못한다.
                        .requestMatchers("/admin/system", "/admin/system/**").hasRole("ADMIN")
                        // 위에서 명시하지 않은 관리자 경로는 시스템 관리자에게만 허용한다.
                        .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                        .requestMatchers("/mypage", "/mypage/**").authenticated()
                        // RES-03 참가 신청: 상세(/matches/{id})는 공개, 선택·신청·완료는 로그인 필요
                        .requestMatchers("/matches/*/selection", "/matches/*/apply", "/matches/*/apply/**")
                        .authenticated()
                        // 동호회: 목록(GET /teams)만 공개, 상세·관리·만들기는 로그인 필요
                        .requestMatchers(HttpMethod.GET, "/teams").permitAll()
                        .requestMatchers("/teams", "/teams/**").authenticated()

                        .anyRequest().permitAll())
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
                        .key(rememberMeKey())
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
