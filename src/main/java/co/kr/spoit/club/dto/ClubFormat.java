package co.kr.spoit.club.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    // 종목 아이콘 파일 이름(images/club/sports) 모르는 종목은 별(etc)
    static String sportKey(Long sportId) {
        if (sportId == null) {
            return "etc";
        }
        return switch (sportId.intValue()) {
            case 1 -> "tennis";
            case 2 -> "soccer";
            case 3 -> "jokgu";
            case 4 -> "basketball";
            case 5 -> "badminton";
            default -> "etc";
        };
    }

    // 동호회 실력 테두리 파일 이름(images/club/tiers) 배지랑 같이 TierRank로 정함
    static String tierKey(Integer averageMmr) {
        if (averageMmr == null) {
            return "none";
        }
        return TierRank.of(BigDecimal.valueOf(averageMmr)).tier().name().toLowerCase(Locale.ROOT);
    }

    // 동호회별 배경색 번호 1~6
    static int markTone(Long clubId) {
        return (int) (clubId % 6) + 1;
    }

    // 팀 실력 등급
    static String tierLabel(Integer averageMmr) {
        if (averageMmr == null) {
            return "기록 없음";
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
