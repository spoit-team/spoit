package co.kr.spoit.member.dto;

/**
 * 사용자 관리 목록의 페이지 링크 모델이다.
 *
 * <p>{@code prevUrl}/{@code nextUrl}은 화면이 그대로 {@code th:href}에 넣는 상대 URL이며, 필터
 * 조건을 보존한다. 첫 페이지에는 이전 링크가, 마지막 페이지에는 다음 링크가 없다.</p>
 */
public class AdminUserPagination {

    private final int page;
    private final int totalPages;
    private final String prevUrl;
    private final String nextUrl;

    public AdminUserPagination(int page, int totalPages, String prevUrl, String nextUrl) {
        this.page = page;
        this.totalPages = totalPages;
        this.prevUrl = prevUrl;
        this.nextUrl = nextUrl;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public String getPrevUrl() {
        return prevUrl;
    }

    public String getNextUrl() {
        return nextUrl;
    }
}
