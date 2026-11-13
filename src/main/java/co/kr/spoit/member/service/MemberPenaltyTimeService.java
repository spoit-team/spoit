package co.kr.spoit.member.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.security.MemberSessionRevocationService;
import lombok.RequiredArgsConstructor;

/**
 * 제재 결과를 {@code app_user.penalty_time}에 반영하는 회원 도메인 서비스다.
 *
 * <p>{@code penalty} 테이블은 신고 도메인이 소유하지만, 로그인 차단에 실제로 쓰이는 회원 컬럼은
 * 회원 도메인이 소유한다. 그래서 제재 서비스는 이 클래스를 통해서만 {@code penalty_time}을
 * 바꾼다. 회원 행은 {@link MemberAccountLockService}로 잠그므로 이용정지·탈퇴·제재 반영이 같은
 * 행에서 직렬화된다.</p>
 *
 * <p>항상 호출자가 연 쓰기 트랜잭션 안에서만 동작한다({@code MANDATORY}). 제재 행과 회원 컬럼이
 * 서로 어긋난 채 커밋되는 일을 막기 위해서다.</p>
 */
@Service
@RequiredArgsConstructor
public class MemberPenaltyTimeService {

    /**
     * 영구 정지를 뜻하는 이용정지 종료 시각이다.
     *
     * <p>{@code app_user.penalty_time}은 {@code null}이면 해제로 읽히므로, 영구 정지는 해제 전까지
     * 항상 미래로 비교되는 이 값을 실제 저장 값으로 쓴다. 제재의 {@code penalty.end_at}도 같은 값을
     * 써서 두 컬럼이 서로 어긋나지 않게 한다.</p>
     */
    public static final LocalDateTime PERMANENT_END_AT = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 저장된 종료 시각이 영구 정지를 뜻하는지 판정한다. */
    public static boolean isPermanentEndAt(LocalDateTime endAt) {
        return endAt != null && !endAt.isBefore(PERMANENT_END_AT);
    }

    private final MemberMapper memberMapper;
    private final MemberAccountLockService accountLockService;
    private final MemberSessionRevocationService memberSessionRevocationService;

    /**
     * 이용정지 종료 시각을 연장한다. 이미 더 늦은 종료 시각이 있으면 그대로 둔다.
     *
     * <p>새로 적용되는 이용정지가 있으면(종료 시각이 미래) 커밋 이후 그 회원의 활성 HTTP 세션·알림
     * 웹소켓 연결을 폐기하도록 예약한다. 롤백되면 폐기하지 않는다.</p>
     *
     * @return 반영 뒤의 종료 시각(기존 값이 더 늦으면 기존 값)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDateTime extendUntil(Long userId, LocalDateTime endAt) {
        if (userId == null || userId <= 0 || endAt == null) {
            throw new IllegalArgumentException("이용정지 대상과 기간을 확인해 주세요.");
        }
        Member member = lock(userId);
        LocalDateTime current = member.getPenaltyTime();
        if (current != null && current.isAfter(endAt)) {
            return current;
        }
        memberMapper.updatePenaltyTime(userId, endAt);
        if (endAt.isAfter(LocalDateTime.now(ZONE))) {
            this.memberSessionRevocationService.revokeAfterCommit(userId);
        }
        return endAt;
    }

    /**
     * 이용정지 종료 시각을 주어진 값으로 덮어쓴다. 남은 제재가 없으면 {@code null}로 해제한다.
     * 해제 뒤 남은 이용 제한을 다시 계산한 값을 넘기는 것은 호출자 몫이다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void overwrite(Long userId, LocalDateTime value) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("이용정지 대상을 확인해 주세요.");
        }
        lock(userId);
        memberMapper.updatePenaltyTime(userId, value);
    }

    /**
     * 회원 행을 잠그고 최신 값을 돌려준다.
     *
     * <p>호출자가 제재 행과 회원 컬럼을 함께 바꿀 때, 항상 회원을 먼저 잠그도록 순서를 맞추는 데
     * 쓴다. 잠금 순서가 경로마다 달라지면 교착이 생길 수 있다.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Member lock(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("대상 회원을 확인해 주세요.");
        }
        Member member = accountLockService.lockById(userId);
        if (member == null || member.getDeletedAt() != null) {
            throw new IllegalArgumentException("대상 회원을 찾을 수 없습니다.");
        }
        return member;
    }
}
