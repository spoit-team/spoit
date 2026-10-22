package co.kr.spoit.member.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.member.domain.Member;

/**
 * {@code app_user} 조회 매퍼. SQL은 {@code resources/mapper/member/MemberMapper.xml}에 있다.
 * 비밀번호 비교는 BCrypt가 하므로 SQL에 비밀번호 조건을 넣지 않는다.
 */
@Mapper
public interface MemberMapper {

    /** {@code email}은 유니크 키라 최대 한 건이 반환된다. */
    Optional<Member> findByEmail(@Param("email") String email);
}
