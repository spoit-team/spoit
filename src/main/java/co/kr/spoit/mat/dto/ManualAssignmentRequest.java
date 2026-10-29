package co.kr.spoit.mat.dto;

import java.util.List;

public record ManualAssignmentRequest(List<Long> teamA, List<Long> teamB) {
}
