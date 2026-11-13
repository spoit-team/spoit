package co.kr.spoit.member.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.member.domain.AdminRequestRow;
import co.kr.spoit.member.domain.AdminUserRow;
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
     * 미탈퇴({@code deleted_at IS NULL})이면서 이용정지 기간이 끝났거나 정지 이력이 없을 때만
     * 탈퇴 시각을 기록한다. 변경 행 수로 동시 탈퇴를 판별한다.
     */
    int recordWithdrawal(@Param("userId") Long userId, @Param("deletedAt") LocalDateTime deletedAt,
            @Param("now") LocalDateTime now);

    /** 비밀번호 해시를 갱신한다. 탈퇴 여부만 검사하고 이용정지 시각은 유지한다. */
    int updatePasswordHash(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);

    /**
     * 회원 행을 잠그고 최신 값을 읽는다. 탈퇴·신청·회장 위임이 같은 회원 행에서 직렬화되도록
     * 쓰기 트랜잭션 안에서만 호출한다(잠금 없이 읽는 {@link #findById}와 달리 스냅샷이 아닌 최신 값을 준다).
     */
    Member lockById(@Param("userId") Long userId);

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

    /* ------------------------------------------------------------------
     * 시스템 관리자 사용자 관리. 목록·필터·페이지네이션과 상태 변경에 필요한 app_user 쿼리다.
     * ------------------------------------------------------------------ */

    /** 탈퇴하지 않은 계정을 검색어·역할·상태로 걸러 offset 페이지로 조회한다. */
    List<AdminUserRow> findAdminUsers(@Param("keyword") String keyword,
            @Param("roleCode") String roleCode, @Param("roleLabel") String roleLabel,
            @Param("status") String status, @Param("now") LocalDateTime now, @Param("limit") int limit,
            @Param("offset") int offset);

    /** 검색 조건에 맞는 계정 수. 페이지 계산에 쓴다. */
    int countAdminUsers(@Param("keyword") String keyword, @Param("roleCode") String roleCode,
            @Param("roleLabel") String roleLabel, @Param("status") String status,
            @Param("now") LocalDateTime now);

    /** 탈퇴하지 않은 전체 계정 수. */
    int countActiveUsers();

    /** 대기 중인 관리자 권한 요청 수. */
    int countPendingAdminRequests();

    /** 지정 역할(코드·한글 표기 허용)의 탈퇴하지 않은 계정 수. */
    int countActiveUsersByRole(@Param("roleCode") String roleCode, @Param("roleLabel") String roleLabel);

    /** 현재 이용이 제한된 탈퇴하지 않은 계정 수. */
    int countSuspendedUsers(@Param("now") LocalDateTime now);

    /** 회원 역할을 저장한다. */
    int updateRole(@Param("userId") Long userId, @Param("role") String role);

    /** 회원 행의 이용정지 종료 시각을 덮어쓴다. {@code null}이면 해제다. */
    int updatePenaltyTime(@Param("userId") Long userId, @Param("penaltyTime") LocalDateTime penaltyTime);

    /** 해당 회원의 대기 중 관리자 권한 요청을 잠그고 읽는다. 없으면 {@code null}. */
    AdminRequestRow findPendingAdminRequestForUpdate(@Param("userId") Long userId);

    /** 관리자 권한 요청 상태를 바꾸고 검토자·검토 시각을 남긴다. */
    int reviewAdminRequest(@Param("requestId") Long requestId, @Param("status") String status,
            @Param("reviewedBy") Long reviewedBy);
}
