package co.kr.spoit.mat.dto;

/**
 * 확정 참가자의 경기 결과 요약 응답.
 *
 * <p>본인 진영과 결과·레이팅 상태만 담고, 상대 팀장 제출 결과나 다른 참가자 정보는 넣지 않는다.
 * {@code myResult}는 요청자가 팀장일 때 본인이 제출한 결과이고, 일반 참가자이면 {@code null}이다.</p>
 */
public record MatchResultSummary(
        Long matchId,
        String status,
        String resultStatus,
        String ratingStatus,
        String finalResult,
        String mySide,
        String myResult) {
}
