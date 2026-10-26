package co.kr.spoit.member.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.dto.SportOption;
import co.kr.spoit.member.dto.SignupRegion;

/**
 * {@code app_user} 조회·저장 매퍼. SQL은 {@code resources/mapper/member/MemberMapper.xml}에 있다.
 * 비밀번호 비교는 BCrypt가 하므로 SQL에 비밀번호 조건을 넣지 않는다.
 */
@Mapper
public interface MemberMapper {

    /** {@code email}은 유니크 키라 최대 한 건이 반환된다. */
    Optional<Member> findByEmail(@Param("email") String email);

    /** 탈퇴 시 비밀번호 재확인을 위해 식별자로 조회한다. */
    Optional<Member> findById(@Param("userId") Long userId);

    /**
     * 미탈퇴({@code deleted_at IS NULL})이면서 현재 상태가 {@code expectedStatuses}에 속할 때만
     * 탈퇴 시각을 기록한다. 변경 행 수로 동시 탈퇴를 판별한다.
     */
    int recordWithdrawal(@Param("userId") Long userId, @Param("deletedAt") LocalDateTime deletedAt,
            @Param("expectedStatuses") List<String> expectedStatuses);

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

    /** 종목 선택지를 `sport_id` 순으로 조회한다. */
    List<SportOption> findSports();

    /** 선택한 종목이 실제로 존재하는지 확인한다. */
    boolean sportExists(@Param("sportId") Long sportId);

    /** 회원의 저장된 대표 종목을 조회한다. 미설정이면 null. */
    SportOption findRepresentativeSport(@Param("userId") Long userId);

    /** 회원의 닉네임·연락처를 저장한다. 선호 종목은 user_preferred_sport로 별도 저장한다. */
    int updateProfileBasics(@Param("userId") Long userId, @Param("nickname") String nickname,
            @Param("phone") String phone);

    /** 회원의 선호 종목을 저장한다. 회원당 1행이며 기존 행을 대체한다. */
    int upsertUserPreferredSport(@Param("userId") Long userId, @Param("sportId") Long sportId);

    /** 회원의 선호 종목 설정을 삭제한다. 미선택 저장 시 사용한다. */
    int deleteUserPreferredSport(@Param("userId") Long userId);

    /** 회원의 선호 지역을 전부 삭제한다. 지역 저장은 삭제 후 재등록으로 대체한다. */
    int deleteUserRegions(@Param("userId") Long userId);
}
