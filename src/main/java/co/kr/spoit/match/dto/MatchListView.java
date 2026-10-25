package co.kr.spoit.match.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 매칭 목록·카드 화면 모델 (검색 조건 "search"는 컨트롤러가 따로 담는다) */
@Getter
@Builder
public class MatchListView {

    private final List<SportTab> sports;
    private final List<DistrictOption> districts;
    private final List<DateOption> dates;
    private final List<String> hourOptions;
    private final List<String> selectedHours;
    private final String selectedHoursLabel;
    private final String selectedSportName;
    private final String sportImageUrl;
    private final long totalRoomCount;
    private final long roomCount;
    private final long recruitingCount;
    private final long matchedCount;
    private final List<ActiveFilter> activeFilters;
    private final List<MatchCardView> matches;
}
