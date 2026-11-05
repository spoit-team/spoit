package co.kr.spoit.club.dto;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import co.kr.spoit.club.domain.ClubSchedule;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClubScheduleResponse {

    private final Long matchId;
    private final String date;
    private final String dateLabel;
    private final String title;
    private final String place;
    private final String time;

    // 관리 화면 예정 일정 한 줄
    public static ClubScheduleResponse from(ClubSchedule schedule) {
        DateTimeFormatter hourMinute = DateTimeFormatter.ofPattern("HH:mm");
        String start = schedule.getStartAt().format(hourMinute);
        String end = schedule.getEndAt().toLocalDate().isAfter(schedule.getStartAt().toLocalDate())
                ? "익일 " + schedule.getEndAt().format(hourMinute)
                : schedule.getEndAt().format(hourMinute);
        String dateLabel = schedule.getStartAt().format(DateTimeFormatter.ofPattern("MM.dd (E)", Locale.KOREAN));

        return ClubScheduleResponse.builder()
                .matchId(schedule.getMatchId())
                .date(schedule.getStartAt().toLocalDate().toString())
                .dateLabel(dateLabel)
                .title(schedule.getTitle())
                .place(schedule.getFacilityName())
                .time(start + " - " + end)
                .build();
    }
}
