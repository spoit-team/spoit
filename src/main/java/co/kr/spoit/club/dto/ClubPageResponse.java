package co.kr.spoit.club.dto;

import java.util.List;

import lombok.Getter;

// 동호회 목록 첫 화면: 카드 + 전체 개수 + 마지막 장 + 그린 장
@Getter
public class ClubPageResponse {

    private final List<ClubCardResponse> clubs;
    private final int totalCount;
    private final int lastPage;
    private final int page;

    public ClubPageResponse(ClubSearchForm filters, List<ClubCardResponse> clubs, int totalCount) {
        this.clubs = clubs;
        this.totalCount = totalCount;
        // 전체 개수 ÷ 한 장 크기를 올림, 0개여도 1장(첫 화면 getSize는 커져서 상수로 나눔)
        this.lastPage = Math.max(1, (int) Math.ceil(totalCount / (double) ClubSearchForm.PAGE_SIZE));
        // 첫 화면에 그린 장, 주소 page가 마지막 장보다 커도 마지막 장까지만
        this.page = Math.min(filters.getShownPage(), lastPage);
    }

}
