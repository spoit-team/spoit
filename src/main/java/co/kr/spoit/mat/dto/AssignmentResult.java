package co.kr.spoit.mat.dto;

import co.kr.spoit.mat.domain.TeamAssignment;

/** 팀 배정 판정·저장 결과. 자동 배정 미리보기가 READY일 때만 assignment가 채워진다. */
public record AssignmentResult(Outcome outcome, String message, TeamAssignment assignment) {

    public enum Outcome {
        READY,
        ASSIGNED,
        RESET,
        NOT_FOUND,
        INVALID_TEAMS,
        NOT_RECRUITING,
        ALREADY_ASSIGNED,
        NOT_ASSIGNED,
        STARTED,
        UNSUPPORTED_CAPACITY,
        NOT_ENOUGH_PARTICIPANTS
    }

    public static AssignmentResult ready(TeamAssignment assignment) {
        return new AssignmentResult(Outcome.READY, "저장 전 자동 배정 결과입니다.", assignment);
    }

    public static AssignmentResult assigned() {
        return new AssignmentResult(Outcome.ASSIGNED, "팀 배정을 확정했습니다.", null);
    }

    public static AssignmentResult reset() {
        return new AssignmentResult(Outcome.RESET, "팀 배정을 취소했습니다.", null);
    }

    public static AssignmentResult notFound() {
        return rejected(Outcome.NOT_FOUND, "매칭방 정보를 찾을 수 없습니다.");
    }

    public static AssignmentResult rejected(Outcome outcome, String message) {
        return new AssignmentResult(outcome, message, null);
    }
}
