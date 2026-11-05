package co.kr.spoit.member.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;

/**
 * 회원 계정({@code app_user}) 행 잠금만 담당한다.
 *
 * <p>탈퇴가 회원의 매치 신청·동호회장 위임과 겹쳐도 "탈퇴했는데 진행 중 신청이 남는" 상태가 생기지 않도록,
 * 같은 회원 행을 쓰는 경로가 모두 이 서비스를 통해 잠근다. {@link MemberMapper}에만 의존하므로
 * match·club 서비스가 이 클래스를 참조해도 순환 의존이 생기지 않는다.</p>
 *
 * <p>{@code MANDATORY} 전파로 호출자가 이미 연 쓰기 트랜잭션 안에서만 동작하게 한다. 잠금은 트랜잭션 종료 시
 * 풀리므로, 잠금만 따로 커밋되는 실수를 막기 위해서다.</p>
 */
@Service
@RequiredArgsConstructor
public class MemberAccountLockService {

    private final MemberMapper memberMapper;

    /**
     * 회원 행을 잠그고 잠근 시점의 최신 값을 돌려준다. 없으면 null.
     *
     * <p>호출자는 이 값으로 탈퇴·이용정지를 다시 판단해야 한다. 잠금 전에 읽은 값은 스냅샷일 수 있다.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Member lockById(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return memberMapper.lockById(userId);
    }

    /**
     * 여러 회원을 식별자 오름차순으로 잠근다. 항상 같은 순서로 잠가 교착을 피한다.
     *
     * @return 잠근 회원 (요청 순서와 무관하게 식별자 오름차순)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Member> lockAllById(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        TreeSet<Long> ordered = new TreeSet<>();
        for (Long userId : userIds) {
            if (userId != null && userId > 0) {
                ordered.add(userId);
            }
        }
        List<Member> locked = new ArrayList<>(ordered.size());
        for (Long userId : ordered) {
            Member member = memberMapper.lockById(userId);
            if (member != null) {
                locked.add(member);
            }
        }
        return locked;
    }
}
