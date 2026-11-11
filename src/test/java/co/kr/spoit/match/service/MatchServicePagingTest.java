package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.kr.spoit.match.dto.MatchCardPage;
import co.kr.spoit.match.dto.MatchCardView;
import co.kr.spoit.match.dto.MatchCountSummary;
import co.kr.spoit.match.dto.MatchCursor;
import co.kr.spoit.match.dto.MatchListView;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.MatchSearchCondition;
import co.kr.spoit.match.dto.MatchSearchQuery;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.match.mapper.MatchMapper;

/** 매칭방 목록 무한 스크롤(커서 페이지)과 상단 배경 사진 선택을 검증한다. */
class MatchServicePagingTest {

    private static final LocalDateTime START = LocalDateTime.of(2030, 1, 1, 10, 0);
    private static final int SIZE = MatchService.PAGE_SIZE;

    private MatchMapper matchMapper;
    private MatchService service;

    @BeforeEach
    void setUp() {
        matchMapper = mock(MatchMapper.class);
        MatchCardAssembler assembler = mock(MatchCardAssembler.class);
        service = new MatchService(matchMapper, mock(MatchApplicationMapper.class), assembler,
                mock(MatchDetailAssembler.class));
        when(assembler.toCard(any(), anyList(), any(), any())).thenAnswer(inv -> {
            MatchRow row = inv.getArgument(0);
            return MatchCardView.builder().matchId(row.getMatchId()).photoUrl(row.getPhotoUrl()).build();
        });
        when(matchMapper.countMatches(any())).thenReturn(new MatchCountSummary());
    }

    @Test
    @DisplayName("한 페이지보다 하나 더 있으면 PAGE_SIZE개만 주고 마지막 카드로 다음 커서를 만든다")
    void hasNextPage() {
        when(matchMapper.findMatches(any())).thenReturn(rows(SIZE + 1));

        MatchCardPage page = service.searchMore(new MatchSearchCondition(), null);

        assertThat(page.matches()).hasSize(SIZE);
        assertThat(MatchCursor.decode(page.nextCursor()))
                .contains(new MatchCursor(START.plusHours(SIZE - 1), SIZE));
    }

    @Test
    @DisplayName("딱 PAGE_SIZE개면 마지막 페이지 (다음 커서 없음)")
    void lastPage() {
        when(matchMapper.findMatches(any())).thenReturn(rows(SIZE));

        MatchCardPage page = service.searchMore(new MatchSearchCondition(), null);

        assertThat(page.matches()).hasSize(SIZE);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("커서 위치와 PAGE_SIZE + 1 제한을 쿼리에 넘긴다")
    void passesCursorAndLimit() {
        when(matchMapper.findMatches(any())).thenReturn(List.of());
        MatchCursor cursor = new MatchCursor(START, 7L);

        service.searchMore(new MatchSearchCondition(), cursor.encode());

        MatchSearchQuery query = captureFindMatches();
        assertThat(query.getCursorStartAt()).isEqualTo(START);
        assertThat(query.getCursorMatchId()).isEqualTo(7L);
        assertThat(query.getLimit()).isEqualTo(SIZE + 1);
    }

    @Test
    @DisplayName("형식이 틀린 커서는 첫 페이지로 처리한다")
    void invalidCursorMeansFirstPage() {
        when(matchMapper.findMatches(any())).thenReturn(List.of());

        service.searchMore(new MatchSearchCondition(), "broken");

        assertThat(captureFindMatches().getCursorStartAt()).isNull();
    }

    @Test
    @DisplayName("첫 화면 집계 쿼리에는 커서·제한이 붙지 않는다")
    void countsIgnorePaging() {
        when(matchMapper.findMatches(any())).thenReturn(rows(SIZE + 1));

        service.search(new MatchSearchCondition(), "/matches");

        ArgumentCaptor<MatchSearchQuery> captor = ArgumentCaptor.forClass(MatchSearchQuery.class);
        verify(matchMapper, org.mockito.Mockito.atLeastOnce()).countMatches(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(q -> {
            assertThat(q.getLimit()).isNull();
            assertThat(q.getCursorStartAt()).isNull();
        });
    }

    @Test
    @DisplayName("첫 페이지에 사진이 없고 다음 페이지가 있으면 전체 목록 기준 첫 사진을 DB에서 찾는다")
    void heroFromLaterPage() {
        when(matchMapper.findMatches(any())).thenReturn(rows(SIZE + 1));
        when(matchMapper.findFirstPhotoUrl(any())).thenReturn("/uploads/later.jpg");

        MatchListView view = service.search(new MatchSearchCondition(), "/matches");

        assertThat(view.getSportImageUrl()).isEqualTo("/uploads/later.jpg");
        assertThat(view.getNextCursor()).isNotNull();
    }

    @Test
    @DisplayName("첫 페이지에 사진이 있으면 DB를 다시 찾지 않는다")
    void heroFromFirstPage() {
        List<MatchRow> rows = rows(SIZE + 1);
        rows.get(3).setPhotoUrl("/uploads/first.jpg");
        when(matchMapper.findMatches(any())).thenReturn(rows);

        MatchListView view = service.search(new MatchSearchCondition(), "/matches");

        assertThat(view.getSportImageUrl()).isEqualTo("/uploads/first.jpg");
        verify(matchMapper, never()).findFirstPhotoUrl(any());
    }

    @Test
    @DisplayName("다음 페이지가 없으면 사진이 없어도 DB를 다시 찾지 않는다")
    void heroWithoutNextPage() {
        when(matchMapper.findMatches(any())).thenReturn(rows(2));

        MatchListView view = service.search(new MatchSearchCondition(), "/matches");

        assertThat(view.getSportImageUrl()).isNull();
        assertThat(view.getNextCursor()).isNull();
        verify(matchMapper, never()).findFirstPhotoUrl(any());
    }

    private MatchSearchQuery captureFindMatches() {
        ArgumentCaptor<MatchSearchQuery> captor = ArgumentCaptor.forClass(MatchSearchQuery.class);
        verify(matchMapper).findMatches(captor.capture());
        return captor.getValue();
    }

    /** matchId 1..n, 한 시간 간격 */
    private static List<MatchRow> rows(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> {
            MatchRow row = new MatchRow();
            row.setMatchId(id);
            row.setStartAt(START.plusHours(id - 1));
            return row;
        }).collect(Collectors.toList());
    }
}
