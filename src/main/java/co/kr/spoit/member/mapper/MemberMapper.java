package co.kr.spoit.member.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.dto.SignupRegion;

/**
 * {@code app_user} 조회·저장 매퍼. SQL은 {@code resources/mapper/member/MemberMapper.xml}에 있다.
 * 비밀번호 비교는 BCrypt가 하므로 SQL에 비밀번호 조건을 넣지 않는다.
 */
@Mapper
public interface MemberMapper {

    /** {@code email}은 유니크 키라 최대 한 건이 반환된다. */
    Optional<Member> findByEmail(@Param("email") String email);

    /** 회원을 저장하고 {@code useGeneratedKeys}로 {@code userId}를 돌려준다. */
    int insert(Member member);

    /** 회원가입 화면의 지역 선택지(하위 자치구)를 조회한다. */
    List<SignupRegion> findSignupRegions();

    /** 선택한 지역이 실제로 존재하는지 확인한다. */
    boolean regionExists(@Param("regionId") Long regionId);

    /** 회원의 선호 지역을 저장한다. */
    int insertUserRegion(@Param("userId") Long userId, @Param("regionId") Long regionId);

    /** 회원이 선택한 선호 지역을 `region_id` 순으로 조회한다. */
    List<SignupRegion> findRegions(@Param("userId") Long userId);
}
