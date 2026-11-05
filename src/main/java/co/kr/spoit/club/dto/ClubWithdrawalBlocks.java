package co.kr.spoit.club.dto;

import java.util.List;

import lombok.Getter;

/**
 * 클럽 도메인이 판단하는 회원 탈퇴 차단 정보.
 *
 * <p>동호회장으로 있는 팀과 모집 중인 팀 매치에 신청·참가 중인 팀만 담는다. 개인 매치처럼
 * 다른 도메인에 속한 차단 사유는 이 DTO에 넣지 않는다. 목록은 항상 비어 있지 않은 값이며,
 * 동호회는 {@code club_id} 기준으로 한 번씩만 담아 서로 다른 동호회가 같은 이름이어도 가려지지 않는다.</p>
 */
@Getter
public class ClubWithdrawalBlocks {

    private final List<String> leaderClubNames;
    private final List<String> teamMatchClubNames;

    private ClubWithdrawalBlocks(List<String> leaderClubNames, List<String> teamMatchClubNames) {
        this.leaderClubNames = List.copyOf(leaderClubNames == null ? List.of() : leaderClubNames);
        this.teamMatchClubNames = List.copyOf(teamMatchClubNames == null ? List.of() : teamMatchClubNames);
    }

    public static ClubWithdrawalBlocks of(List<String> leaderClubNames, List<String> teamMatchClubNames) {
        return new ClubWithdrawalBlocks(leaderClubNames, teamMatchClubNames);
    }

    public static ClubWithdrawalBlocks empty() {
        return new ClubWithdrawalBlocks(List.of(), List.of());
    }

    public boolean isBlocked() {
        return !leaderClubNames.isEmpty() || !teamMatchClubNames.isEmpty();
    }
}
