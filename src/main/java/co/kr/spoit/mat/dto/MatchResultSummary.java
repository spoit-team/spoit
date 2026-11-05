package co.kr.spoit.mat.dto;

import java.util.List;

/**
 * 확정 참가자의 경기 결과 요약 응답.
 *
 * <p>본인 진영과 결과·레이팅 상태, 화면에 보여 줄 경기 정보(일시·시설·제출 마감)와 양 팀 로스터만 담는다.
 * 상대 팀장 제출 결과나 회원 번호 같은 식별자는 넣지 않는다.
 * {@code myResult}는 요청자가 팀장일 때 본인이 제출한 결과이고, 일반 참가자이면 {@code null}이다.</p>
 */
public record MatchResultSummary(
        Long matchId,
        String status,
        String resultStatus,
        String ratingStatus,
        String finalResult,
        String mySide,
        String myResult,
        String scheduleLabel,
        String facilityName,
        String deadlineLabel,
        List<Team> teams) {

    /** 한 팀의 로스터. 화면에는 진영과 확정 참가자 닉네임만 내보낸다. */
    public record Team(String side, boolean mine, List<Member> members) {
    }

    /** 확정 참가자 한 명. 닉네임과 팀장 여부만 담는다. */
    public record Member(String nickname, boolean captain) {
    }
}
