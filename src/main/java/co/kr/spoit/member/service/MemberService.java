package co.kr.spoit.member.service;

import java.util.List;
import java.util.Optional;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.dto.SignupForm;
import co.kr.spoit.member.dto.SignupRegion;

/**
 * 회원 조회·가입 계약. 컨트롤러가 매퍼를 직접 호출하지 않도록 유지한다.
 */
public interface MemberService {

    /** 이메일을 정규화해 회원을 조회한다. 없거나 빈 입력이면 빈 값. */
    Optional<Member> findByEmail(String email);

    /** 일반 회원을 가입시킨다. 검증 실패 시 {@link IllegalArgumentException}을 던진다. */
    void register(SignupForm form);

    /** 회원가입 화면에 노출할 지역 선택지를 조회한다. */
    List<SignupRegion> findSignupRegions();

    /** 회원이 선택한 선호 지역을 조회한다. 없으면 빈 목록. */
    List<SignupRegion> findRegions(Long userId);
}
