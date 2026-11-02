package co.kr.spoit.mat.domain;

import java.math.BigDecimal;

/** social_match.final_result 값. DB에는 상수 이름 그대로 저장한다. */
public enum MatchResult {

    A_WIN("1.0", "0.0"),
    DRAW("0.5", "0.5"),
    B_WIN("0.0", "1.0");

    private static final String SIDE_A = "A";

    private final BigDecimal teamAScore;
    private final BigDecimal teamBScore;

    MatchResult(String teamAScore, String teamBScore) {
        this.teamAScore = new BigDecimal(teamAScore);
        this.teamBScore = new BigDecimal(teamBScore);
    }

    public BigDecimal actualScoreOf(String side) {
        return SIDE_A.equals(side) ? teamAScore : teamBScore;
    }
}
