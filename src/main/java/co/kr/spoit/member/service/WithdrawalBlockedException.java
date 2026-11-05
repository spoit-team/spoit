package co.kr.spoit.member.service;

/**
 * 동호회장이거나 모집 중인 팀 매치에 신청된 상태라 탈퇴를 진행할 수 없을 때 던진다.
 *
 * <p>세션은 유지한 채 안내 메시지만 되돌리기 위해, 컨트롤러가 로그아웃 처리 전에 잡아낸다.</p>
 */
public class WithdrawalBlockedException extends IllegalStateException {

    public WithdrawalBlockedException(String message) {
        super(message);
    }
}
