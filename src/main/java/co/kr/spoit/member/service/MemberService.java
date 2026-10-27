package co.kr.spoit.member.service;

import java.util.List;
import java.util.Optional;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.dto.ProfileUpdateForm;
import co.kr.spoit.member.dto.SignupForm;
import co.kr.spoit.member.dto.SportOption;
import co.kr.spoit.member.dto.SignupRegion;

/**
 * 회원 조회·가입 계약. 컨트롤러가 매퍼를 직접 호출하지 않도록 유지한다.
 */
public interface MemberService {

    /** 이메일을 정규화해 회원을 조회한다. 없거나 빈 입력이면 빈 값. */
    Optional<Member> findByEmail(String email);

    /**
     * 일반 회원을 가입시킨다. 선택 지역은 user_region, 선택 선호 종목은 user_preferred_sport에 함께 저장한다.
     * 검증 실패 시 {@link IllegalArgumentException}을 던진다.
     */
    void register(SignupForm form);

    /** 회원가입 화면에 노출할 지역 선택지를 조회한다. */
    List<SignupRegion> findSignupRegions();

    /** 회원이 선택한 선호 지역을 조회한다. 없으면 빈 목록. */
    List<SignupRegion> findRegions(Long userId);

    /** 종목 선택지를 조회한다. */
    List<SportOption> findSports();

    /** 회원의 저장된 대표 종목을 조회한다. 미설정이면 빈 Optional. */
    Optional<SportOption> findRepresentativeSport(Long userId);

    /** 닉네임·연락처·대표 종목·선호 지역을 저장한다. 검증 실패 시 {@link IllegalArgumentException}. */
    void updateProfile(Long userId, ProfileUpdateForm form);

    /** 비밀번호를 재확인한 뒤 회원의 탈퇴 시각(deleted_at)을 기록한다. 소프트 삭제라 다른 행은 건드리지 않는다. */
    void withdraw(Long userId, String rawPassword);
}
