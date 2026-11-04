package co.kr.spoit.club.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.mat.domain.TierRank;

class ClubFormat {

    // 요일 번호 1=월 ~ 7=일 (0은 비워둠)
    private static final String[] DAYS = { "", "월", "화", "수", "목", "금", "토", "일" };

    static boolean isRecruiting(ClubSummary club) {
        return "모집중".equals(club.getRecruitStatus())
                && club.getMemberCount() < club.getMaxMembers();
    }

    // 관리 화면 모집 문구: 팀장이 닫음 / 정원 참 구분
    static String manageRecruitLabel(ClubSummary club) {
        if (isRecruiting(club)) {
            return "모집 중";
        }
        if ("마감".equals(club.getRecruitStatus())) {
            return "모집 마감";
        }
        return "모집 마감 (정원이 찼어요)";
    }

    static String areaLabel(String areaLabel) {
        return areaLabel != null ? areaLabel : "지역 미정";
    }

    // 종목 탭 임시 이모지(팀 아이콘 PR에서 SVG로 교체)
    static String sportEmoji(Long sportId) {
        if (sportId == null)
            return "🏅";
        return switch (sportId.intValue()) {
            case 1 -> "🎾";
            case 2 -> "⚽";
            case 3 -> "🏐";
            case 4 -> "🏀";
            case 5 -> "🏸";
            default -> "🏅";
        };
    }

    // 팀 실력 등급
    static String tierLabel(Integer averageMmr) {
        if (averageMmr == null) {
            return "배치 중";
        }
        return TierRank.of(BigDecimal.valueOf(averageMmr)).tier().label();
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
