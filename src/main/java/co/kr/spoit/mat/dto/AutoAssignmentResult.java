package co.kr.spoit.mat.dto;

import co.kr.spoit.mat.domain.TeamAssignment;

/** 자동 배정 판정 결과. READY일 때만 assignment가 채워진다. */
public record AutoAssignmentResult(Outcome outcome, String message, TeamAssignment assignment) {

    public enum Outcome {
        READY,
        NOT_FOUND,
        NOT_RECRUITING,
        ALREADY_ASSIGNED,
        STARTED,
        UNSUPPORTED_CAPACITY,
        NOT_ENOUGH_PARTICIPANTS
    }

    public static AutoAssignmentResult ready(TeamAssignment assignment) {
        return new AutoAssignmentResult(Outcome.READY, "저장 전 자동 배정 결과입니다.", assignment);
    }

    public static AutoAssignmentResult rejected(Outcome outcome, String message) {
        return new AutoAssignmentResult(outcome, message, null);
    }
}
