package co.kr.spoit.match.dto;

import java.util.List;

/**
 * 무한 스크롤로 이어 붙이는 매칭방 카드 한 페이지.
 *
 * @param nextCursor 다음 페이지 커서, 마지막 페이지면 null
 */
public record MatchCardPage(List<MatchCardView> matches, String nextCursor) {
}
