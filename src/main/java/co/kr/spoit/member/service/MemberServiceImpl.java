package co.kr.spoit.member.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;

/**
 * {@link MemberService} 구현. 입력을 trim·소문자로 정규화한 뒤 매퍼에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;

    @Override
    public Optional<Member> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return memberMapper.findByEmail(email.trim().toLowerCase(Locale.ROOT));
    }
}
