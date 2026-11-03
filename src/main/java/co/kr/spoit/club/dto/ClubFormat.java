package co.kr.spoit.club.dto;

import java.util.ArrayList;
import java.util.List;

import co.kr.spoit.club.domain.ClubSummary;

class ClubFormat {

    // 요일 번호 1=월 ~ 7=일 (0은 비워둠)
    private static final String[] DAYS = { "", "월", "화", "수", "목", "금", "토", "일" };

    static boolean isRecruiting(ClubSummary club) {
        return "모집중".equals(club.getRecruitStatus())
                && club.getMemberCount() < club.getMaxMembers();
    }

    static String areaLabel(String areaLabel) {
        return areaLabel != null ? areaLabel : "지역 미정";
    }

    static String initials(String name) {
        return name.length() <= 2 ? name : name.substring(0, 2);
    }

    // "2,4" -> [2, 4] -> "화, 목"
    static List<Integer> days(String activityDays) {
        List<Integer> days = new ArrayList<>();
        for (String number : activityDays.split(",")) {
            days.add(Integer.parseInt(number));
        }
        return days;
    }

    // 종료가 시작보다 빠르면 "익일"
    static String time(ClubSummary club) {
        String[] numbers = club.getActivityDays().split(",");
        String[] names = new String[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            names[i] = DAYS[Integer.parseInt(numbers[i])];
        }
        String endText = club.getActivityEndTime().isBefore(club.getActivityStartTime())
                ? "익일 " + club.getActivityEndTime()
                : String.valueOf(club.getActivityEndTime());
        return String.join(", ", names) + " " + club.getActivityStartTime() + " - " + endText;
    }

    static String footerNote(boolean recruiting, ClubSummary club) {
        if (!recruiting) {
            return "모집이 마감된 팀입니다.";
        }
        return "모집 자리: " + (club.getMaxMembers() - club.getMemberCount()) + "명";
    }
}
