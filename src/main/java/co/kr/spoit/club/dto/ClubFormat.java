package co.kr.spoit.club.dto;

import co.kr.spoit.club.domain.ClubSummary;

class ClubFormat {

    private static final String[] DAYS = {"", "월", "화", "수", "목", "금", "토", "일"};

    static boolean isRecruiting(String recruitStatus) {
        return "모집중".equals(recruitStatus);
    }

    static String areaLabel(String areaLabel) {
        return areaLabel != null ? areaLabel : "지역 미정";
    }

    static String initials(String name) {
        return name.length() <= 2? name : name.substring(0, 2);
    }

    static String time(ClubSummary club) {
        String[] numbers = club.getActivityDays().split(",");
        String[] names = new String[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            names[i] = DAYS[Integer.parseInt(numbers[i])];
        }
        return String.join(", ", names) + " " + club.getActivityStartTime() + " - " + club.getActivityEndTime();
    }
    
    static String footerNote(boolean recruiting, ClubSummary club) {
        if (!recruiting) {
            return "모집이 마감된 팀입니다.";
        }
        return "모집 자리: " + (club.getMaxMembers() - club.getMemberCount()) + "명";
    }
}
