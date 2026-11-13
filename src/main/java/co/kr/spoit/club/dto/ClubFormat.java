package co.kr.spoit.club.dto;

import co.kr.spoit.global.common.SportIconKey;
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

    // 관리 화면 모집 문구: 동호회장이 닫았거나 정원이 찼으면 모집 마감
    static String manageRecruitLabel(ClubSummary club) {
        return isRecruiting(club) ? "모집 중" : "모집 마감";
    }

    static String areaLabel(String areaLabel) {
        return areaLabel != null ? areaLabel : "지역 미정";
    }

    // 종목 아이콘 파일 이름(images/club/sports) 모르는 종목은 별(etc)
    static String sportKey(Long sportId) {
        return SportIconKey.of(sportId);
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

    // 동호회 실력 등급: 배치 마친 동호회원이 없으면 배치 중
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
}
