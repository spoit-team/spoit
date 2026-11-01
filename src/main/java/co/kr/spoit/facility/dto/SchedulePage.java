package co.kr.spoit.facility.dto;

import java.util.List;

import co.kr.spoit.facility.dto.CalendarView;
import co.kr.spoit.facility.dto.ConditionsView;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.dto.SportOption;
import co.kr.spoit.facility.dto.WeeklyHourRow;
import lombok.Getter;

/**
 * 예약 운영 일정 화면의 모델 묶음이다.
 *
 * <p>컨트롤러가 이 값을 그대로 모델 속성으로 옮긴다. 화면 변수 이름과 순서를 한곳에서
 * 관리해 템플릿 계약이 흩어지지 않게 한다.</p>
 */
@Getter
public class SchedulePage {

    private final FacilityListItem selectedFacility;
    private final List<WeeklyHourRow> weeklyHours;
    private final List<String> hourOptions;
    private final CalendarView calendar;
    private final List<SportOption> sports;
    private final ConditionsView conditions;
    private final List<ClosureRow> closures;
    private final String tab;

    public SchedulePage(FacilityListItem selectedFacility, List<WeeklyHourRow> weeklyHours,
            List<String> hourOptions, CalendarView calendar, List<SportOption> sports,
            ConditionsView conditions, List<ClosureRow> closures, String tab) {
        this.selectedFacility = selectedFacility;
        this.weeklyHours = List.copyOf(weeklyHours);
        this.hourOptions = List.copyOf(hourOptions);
        this.calendar = calendar;
        this.sports = List.copyOf(sports);
        this.conditions = conditions;
        this.closures = List.copyOf(closures);
        this.tab = tab;
    }

    public static SchedulePage empty() {
        return new SchedulePage(null, List.of(), List.of(), null, List.of(),
                ConditionsView.empty(), List.of(), null);
    }
}
