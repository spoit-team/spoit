package co.kr.spoit.mat.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 팀장의 평가 화면 조회 응답. 항목은 표시 순서대로 담는다. */
public record MatchEvaluationView(
        Long matchId,
        String mySide,
        List<Target> targets,
        List<Item> items,
        boolean submitted,
        boolean canSubmit,
        LocalDateTime deadlineAt) {

    public record Target(Long participantId, String nickname, String matchSide) {
    }

    public record Item(Long itemId, String itemName) {
    }
}
