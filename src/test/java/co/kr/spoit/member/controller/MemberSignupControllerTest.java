package co.kr.spoit.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.dto.SignupForm;
import co.kr.spoit.member.dto.SignupRegion;
import co.kr.spoit.member.mapper.MemberMapper;

/**
 * 회원가입 저장·검증 경계만 확인한다. 매퍼는 목으로 대체해 SQL 실행은 다루지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MemberSignupControllerTest {

    private static final String EMAIL = " Player@Example.COM ";
    private static final String NORMALIZED_EMAIL = "player@example.com";
    private static final String PHONE = " 010-1234-5678 ";
    private static final String PASSWORD = "password1234";
    private static final String NICKNAME = " 플레이어 ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MemberMapper memberMapper;

    /** 매퍼를 목으로 쓰는 컨트롤러 테스트라 실제 DB 접속 없이 트랜잭션 경계만 통과시킨다. */
    @TestConfiguration
    static class NoDatabaseTransactionConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }
    }

    private static Map<String, String> validParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("agreeService", "true");
        params.put("agreePrivacy", "true");
        params.put("marketingOptIn", "false");
        params.put("role", "member");
        params.put("email", EMAIL);
        params.put("phone", PHONE);
        params.put("password", PASSWORD);
        params.put("passwordConfirm", PASSWORD);
        params.put("nickname", NICKNAME);
        return params;
    }

    private MockHttpServletRequestBuilder signup(Map<String, String> overrides) {
        Map<String, String> params = validParams();
        params.putAll(overrides);
        MockHttpServletRequestBuilder request = post("/signup").with(csrf());
        params.forEach(request::param);
        return request;
    }

    @Test
    @DisplayName("회원가입 화면은 지역 선택지를 모델에 담아 렌더링한다")
    void signupPageRendersRegions() throws Exception {
        SignupRegion region = new SignupRegion();
        region.setRegionId(7L);
        region.setName("강남구");
        given(memberMapper.findSignupRegions()).willReturn(List.of(region));

        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attribute("districts", List.of(region)));
    }

    @Test
    @DisplayName("가입에 성공하면 정규화된 일반 회원을 저장하고 그 계정으로 로그인할 수 있다")
    void signupSavesNormalizedMemberAndAllowsLogin() throws Exception {
        AtomicReference<Member> saved = new AtomicReference<>();
        given(memberMapper.findByEmail(anyString())).willAnswer(invocation -> Optional.ofNullable(saved.get()));
        given(memberMapper.insert(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            member.setUserId(11L);
            saved.set(member);
            return 1;
        });

        MvcResult result = mockMvc.perform(signup(Map.of()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?message=*"))
                .andReturn();

        String redirect = result.getResponse().getRedirectedUrl();
        String message = redirect.substring(redirect.indexOf("message=") + "message=".length());
        assertThat(redirect).contains("%");
        assertThat(URLDecoder.decode(message, StandardCharsets.UTF_8)).contains("회원가입");

        Member member = saved.get();
        assertThat(member.getEmail()).isEqualTo(NORMALIZED_EMAIL);
        assertThat(member.getPhone()).isEqualTo("010-1234-5678");
        assertThat(member.getNickname()).isEqualTo("플레이어");
        assertThat(member.getRole()).isEqualTo("일반 회원");
        assertThat(member.getStatus()).isEqualTo("활성");
        assertThat(passwordEncoder.matches(PASSWORD, member.getPasswordHash())).isTrue();

        mockMvc.perform(post("/login").with(csrf())
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches"));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 저장 없이 오류를 다시 렌더링한다")
    void duplicateEmailIsRejectedBeforeInsert() throws Exception {
        Member existing = new Member();
        existing.setEmail(NORMALIZED_EMAIL);
        given(memberMapper.findByEmail(anyString())).willReturn(Optional.of(existing));

        mockMvc.perform(signup(Map.of()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeExists("error"));

        verify(memberMapper, never()).insert(any(Member.class));
    }

    @Test
    @DisplayName("사전 조회를 통과해도 저장 중 중복 키가 나면 오류를 다시 렌더링한다")
    void duplicateKeyRaceIsRenderedAsError() throws Exception {
        given(memberMapper.insert(any(Member.class)))
                .willThrow(new DuplicateKeyException("duplicate email"));

        mockMvc.perform(signup(Map.of()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("선택한 지역은 생성된 회원 ID로 저장한다")
    void selectedRegionUsesGeneratedMemberId() throws Exception {
        given(memberMapper.regionExists(7L)).willReturn(true);
        given(memberMapper.insert(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            member.setUserId(42L);
            return 1;
        });

        mockMvc.perform(signup(Map.of("regionId", "7")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?message=*"));

        verify(memberMapper).insertUserRegion(42L, 7L);
    }

    static Stream<Arguments> invalidSignups() {
        String overLimitPassword = "a".repeat(72) + "1";
        return Stream.of(
                Arguments.of("일반 회원이 아닌 역할", Map.of("role", "system")),
                Arguments.of("필수 약관 미동의", Map.of("agreeService", "false")),
                Arguments.of("지원하지 않는 마케팅 동의", Map.of("marketingOptIn", "true")),
                Arguments.of("존재하지 않는 지역", Map.of("regionId", "9999")),
                Arguments.of("비밀번호 72바이트 초과",
                        Map.of("password", overLimitPassword, "passwordConfirm", overLimitPassword)),
                Arguments.of("연락처 형식 위반", Map.of("phone", "02-123-45a")),
                Arguments.of("닉네임 길이 위반", Map.of("nickname", "a")),
                Arguments.of("이메일 형식 위반", Map.of("email", "not-an-email")),
                Arguments.of("비밀번호 확인 불일치", Map.of("passwordConfirm", "different1234")),
                Arguments.of("비밀번호 숫자 누락", Map.of("password", "onlyletters", "passwordConfirm", "onlyletters")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSignups")
    @DisplayName("검증을 통과하지 못한 가입 요청은 저장하지 않고 오류를 렌더링한다")
    void invalidSignupIsRejected(String label, Map<String, String> overrides) throws Exception {
        mockMvc.perform(signup(overrides))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeExists("error"));

        verify(memberMapper, never()).insert(any(Member.class));
    }

    @Test
    @DisplayName("바인딩에 실패하면 비밀번호를 지우고 오류를 다시 렌더링한다")
    void malformedBindingClearsSecrets() throws Exception {
        MvcResult result = mockMvc.perform(signup(Map.of(
                        "regionId", "not-a-number",
                        "adminInviteCode", "invite-code-123")))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeExists("error"))
                .andReturn();

        SignupForm form = (SignupForm) result.getModelAndView().getModel().get("signupForm");
        assertThat(form.getPassword()).isNull();
        assertThat(form.getPasswordConfirm()).isNull();
        assertThat(form.getAdminInviteCode()).isNull();
        verify(memberMapper, never()).insert(any(Member.class));
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 회원가입은 거부된다")
    void signupRequiresCsrf() throws Exception {
        Map<String, String> params = validParams();
        MockHttpServletRequestBuilder request = post("/signup");
        params.forEach(request::param);

        mockMvc.perform(request).andExpect(status().isForbidden());
        verify(memberMapper, never()).insert(any(Member.class));
    }
}
