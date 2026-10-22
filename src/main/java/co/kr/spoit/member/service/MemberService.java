package co.kr.spoit.member.service;

import java.util.Optional;

import co.kr.spoit.member.domain.Member;

/**
 * 회원 조회 계약. 컨트롤러가 매퍼를 직접 호출하지 않도록 유지한다.
 */
public interface MemberService {

    /** 이메일을 정규화해 회원을 조회한다. 없거나 빈 입력이면 빈 값. */
    Optional<Member> findByEmail(String email);
}
