package co.kr.spoit.member.dto;

import java.util.List;

/** 사용자 관리 화면 모델(지표·검색 조건·계정 행·페이지)이다. */
public class AdminUserPage {

    private final AdminUserMetrics metrics;
    private final AdminUserFilters filters;
    private final List<AdminUserListItem> users;
    private final AdminUserPagination pagination;

    public AdminUserPage(AdminUserMetrics metrics, AdminUserFilters filters,
            List<AdminUserListItem> users, AdminUserPagination pagination) {
        this.metrics = metrics;
        this.filters = filters;
        this.users = List.copyOf(users);
        this.pagination = pagination;
    }

    /** 아직 조회하지 않은 빈 화면 모델이다. */
    public static AdminUserPage empty() {
        return new AdminUserPage(new AdminUserMetrics(0, 0, 0, 0, 0, 0),
                new AdminUserFilters(null, null, null, 0), List.of(),
                new AdminUserPagination(0, 1, null, null));
    }

    public AdminUserMetrics getMetrics() {
        return metrics;
    }

    public AdminUserFilters getFilters() {
        return filters;
    }

    public List<AdminUserListItem> getUsers() {
        return users;
    }

    public AdminUserPagination getPagination() {
        return pagination;
    }
}
