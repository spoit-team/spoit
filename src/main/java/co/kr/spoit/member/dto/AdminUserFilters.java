package co.kr.spoit.member.dto;

/** 사용자 관리 화면의 검색 조건이다. {@code role}/{@code status}는 화면 선택 값(member·facility·system / pending·active·suspended)이다. */
public class AdminUserFilters {

    private final String q;
    private final String role;
    private final String status;
    private final int page;

    public AdminUserFilters(String q, String role, String status, int page) {
        this.q = q;
        this.role = role;
        this.status = status;
        this.page = page;
    }

    public String getQ() {
        return q;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public int getPage() {
        return page;
    }
}
