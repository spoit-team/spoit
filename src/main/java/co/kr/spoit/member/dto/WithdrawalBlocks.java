package co.kr.spoit.member.dto;

import java.util.List;

import lombok.Getter;

/**
 * 회원 탈퇴 화면·정책이 사용하는 차단 사유 묶음.
 *
 * <p>동호회장으로 있는 팀, 모집 중인 팀 매치에 신청·참가 중인 팀, 진행 중(모집 중·매칭 완료)인
 * 개인 매치 신청을 각각 한 번씩 담는다. 목록은 항상 비어 있지 않은 값(빈 목록 포함)이며,
 * 화면은 {@code blocked}로 막힘 여부를 판단한다.</p>
 */
@Getter
public class WithdrawalBlocks {

    private final List<String> leaderClubNames;
    private final List<String> teamMatchClubNames;
    private final List<String> individualMatchNames;

    private WithdrawalBlocks(List<String> leaderClubNames, List<String> teamMatchClubNames,
            List<String> individualMatchNames) {
        this.leaderClubNames = List.copyOf(leaderClubNames == null ? List.of() : leaderClubNames);
        this.teamMatchClubNames = List.copyOf(teamMatchClubNames == null ? List.of() : teamMatchClubNames);
        this.individualMatchNames = List.copyOf(individualMatchNames == null ? List.of() : individualMatchNames);
    }

    public static WithdrawalBlocks of(List<String> leaderClubNames, List<String> teamMatchClubNames,
            List<String> individualMatchNames) {
        return new WithdrawalBlocks(leaderClubNames, teamMatchClubNames, individualMatchNames);
    }

    public static WithdrawalBlocks empty() {
        return new WithdrawalBlocks(List.of(), List.of(), List.of());
    }

    public boolean isBlocked() {
        return !leaderClubNames.isEmpty() || !teamMatchClubNames.isEmpty() || !individualMatchNames.isEmpty();
    }
}
