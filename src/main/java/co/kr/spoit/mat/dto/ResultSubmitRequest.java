package co.kr.spoit.mat.dto;

import co.kr.spoit.mat.domain.MatchResult;

/** 정해진 값이 아니면 역직렬화 단계에서 400이 된다. */
public record ResultSubmitRequest(MatchResult result) {
}
