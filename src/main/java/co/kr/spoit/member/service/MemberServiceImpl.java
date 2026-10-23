package co.kr.spoit.member.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberRole;
import co.kr.spoit.member.dto.ProfileUpdateForm;
import co.kr.spoit.member.dto.SignupForm;
import co.kr.spoit.member.dto.SignupRegion;
import co.kr.spoit.member.dto.SportOption;
import co.kr.spoit.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;

/**
 * {@link MemberService} 구현. 조회는 입력을 trim·소문자로 정규화한 뒤 매퍼에 위임하고,
 * 가입은 검증·정규화 후 회원과 선택 지역을 한 트랜잭션으로 저장한다.
 */
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    /** {@code app_user.email} 컬럼 길이. */
    private static final int EMAIL_MAX_LENGTH = 255;

    /** BCrypt가 처리할 수 있는 비밀번호 최대 바이트 수. 초과분은 잘려 검증되므로 가입 단계에서 막는다. */
    private static final int PASSWORD_MAX_BYTES = 72;

    private static final String SIGNUP_ROLE_MEMBER = "member";
    private static final String ACTIVE_STATUS = "활성";

    /** 탈퇴 표시 상태. {@code MemberPrincipal}의 로그인 허용 목록에 없어 자동으로 차단된다. */
    private static final String WITHDRAWN_STATUS = "탈퇴";
    /** 자발적 탈퇴를 허용하는 현재 상태. 정지·이미 탈퇴한 계정은 바꾸지 않는다. */
    private static final List<String> WITHDRAWABLE_STATUSES = List.of("활성", "ACTIVE", "NORMAL");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    /** signup.js의 연락처 검증과 같은 규칙이다. */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+() -]{8,20}$");
    /** signup.js의 닉네임 검증과 같은 규칙이다. */
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9_]{2,12}$");

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Optional<Member> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return memberMapper.findByEmail(email.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    @Transactional
    public void register(SignupForm form) {
        if (form == null) {
            throw new IllegalArgumentException("회원가입 정보를 확인해 주세요.");
        }
        if (!form.isAgreeService() || !form.isAgreePrivacy()) {
            throw new IllegalArgumentException("필수 약관에 모두 동의해 주세요.");
        }
        // 약관 동의만 받고 저장하지 않으면 동의를 조용히 버리는 셈이므로 명시적으로 거부한다.
        if (form.isMarketingOptIn()) {
            throw new IllegalArgumentException("혜택 및 이벤트 수신 동의는 아직 지원하지 않습니다.");
        }
        if (!isMemberRole(form.getRole())) {
            throw new IllegalArgumentException("일반 회원 가입만 지원합니다.");
        }

        String email = normalizeEmail(form.getEmail());
        String phone = trim(form.getPhone());
        String nickname = trim(form.getNickname());
        validateEmail(email);
        validatePhone(phone);
        validateNickname(nickname);
        validatePassword(form.getPassword(), form.getPasswordConfirm());

        Long regionId = form.getRegionId();
        if (regionId != null && !memberMapper.regionExists(regionId)) {
            throw new IllegalArgumentException("선택한 지역을 찾을 수 없습니다.");
        }
        Long sportId = form.getRepresentativeSportId();
        if (sportId != null && !memberMapper.sportExists(sportId)) {
            throw new IllegalArgumentException("선택한 종목을 찾을 수 없습니다.");
        }
        if (memberMapper.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        Member member = new Member();
        member.setEmail(email);
        member.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        member.setNickname(nickname);
        member.setPhone(phone);
        member.setRole(MemberRole.MEMBER.label());
        member.setStatus(ACTIVE_STATUS);

        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException exception) {
            // 사전 조회와 저장 사이에 같은 이메일이 먼저 저장된 경우다.
            throw new IllegalArgumentException("이미 가입된 이메일입니다.", exception);
        }

        if (regionId != null) {
            memberMapper.insertUserRegion(member.getUserId(), regionId);
        }
        if (sportId != null) {
            memberMapper.upsertUserPreferredSport(member.getUserId(), sportId);
        }
    }

    @Override
    public List<SignupRegion> findSignupRegions() {
        return memberMapper.findSignupRegions();
    }

    @Override
    public List<SignupRegion> findRegions(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return memberMapper.findRegions(userId);
    }

    @Override
    public List<SportOption> findSports() {
        return memberMapper.findSports();
    }

    @Override
    public Optional<SportOption> findRepresentativeSport(Long userId) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(memberMapper.findRepresentativeSport(userId));
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, ProfileUpdateForm form) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("회원 식별자가 올바르지 않습니다.");
        }
        if (form == null) {
            throw new IllegalArgumentException("입력 정보를 확인해 주세요.");
        }
        String nickname = trim(form.getNickname());
        String phone = trim(form.getPhone());
        validateNickname(nickname);
        validatePhone(phone);

        Long sportId = form.getRepresentativeSportId();
        if (sportId != null && !memberMapper.sportExists(sportId)) {
            throw new IllegalArgumentException("선택한 종목을 찾을 수 없습니다.");
        }
        Long regionId = form.getRegionId();
        if (regionId != null && !memberMapper.regionExists(regionId)) {
            throw new IllegalArgumentException("선택한 지역을 찾을 수 없습니다.");
        }

        memberMapper.updateProfileBasics(userId, nickname, phone);
        if (sportId != null) {
            memberMapper.upsertUserPreferredSport(userId, sportId);
        } else {
            memberMapper.deleteUserPreferredSport(userId);
        }
        memberMapper.deleteUserRegions(userId);
        if (regionId != null) {
            memberMapper.insertUserRegion(userId, regionId);
        }
    }

    @Override
    @Transactional
    public void withdraw(Long userId, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호를 입력해 주세요.");
        }
        Member member = memberMapper.findById(userId)
                .orElseThrow(() -> new IllegalStateException("회원 정보를 찾을 수 없습니다."));
        if (member.getPasswordHash() == null || member.getPasswordHash().isBlank()
                || !passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        // 소프트 삭제이므로 다른 행은 건드리지 않고 상태만 바꾼다. 0행이면 이미 탈퇴했거나 허용 상태가 아니다.
        int updated = memberMapper.updateStatus(userId, WITHDRAWN_STATUS, WITHDRAWABLE_STATUSES);
        if (updated != 1) {
            throw new IllegalStateException("이미 탈퇴했거나 탈퇴할 수 없는 계정입니다.");
        }
    }

    private static boolean isMemberRole(String role) {
        String value = trim(role).toLowerCase(Locale.ROOT);
        return value.isEmpty() || SIGNUP_ROLE_MEMBER.equals(value);
    }

    private static String normalizeEmail(String email) {
        return trim(email).toLowerCase(Locale.ROOT);
    }

    private static void validateEmail(String email) {
        if (email.isEmpty() || email.length() > EMAIL_MAX_LENGTH
                || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식으로 입력해 주세요.");
        }
    }

    private static void validatePhone(String phone) {
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("연락처를 8~20자의 숫자와 전화번호 기호로 입력해 주세요.");
        }
    }

    private static void validateNickname(String nickname) {
        if (!NICKNAME_PATTERN.matcher(nickname).matches()) {
            throw new IllegalArgumentException("닉네임은 한글, 영문, 숫자, 밑줄로 2~12자 입력해 주세요.");
        }
    }

    private static void validatePassword(String password, String confirmation) {
        if (password == null || password.length() < 8 || !hasAsciiLetterAndDigit(password)) {
            throw new IllegalArgumentException("비밀번호는 영문과 숫자를 포함해 8자 이상 입력해 주세요.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new IllegalArgumentException("비밀번호는 72바이트 이하로 입력해 주세요.");
        }
        if (!password.equals(confirmation)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
    }

    private static boolean hasAsciiLetterAndDigit(String value) {
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if ((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z')) {
                letter = true;
            } else if (character >= '0' && character <= '9') {
                digit = true;
            }
        }
        return letter && digit;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
