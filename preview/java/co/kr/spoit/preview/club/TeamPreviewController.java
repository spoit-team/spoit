package co.kr.spoit.preview.club;

import java.util.List;

import co.kr.spoit.preview.club.TeamPageModels.ClubCard;
import co.kr.spoit.preview.club.TeamPageModels.ClubDetail;
import co.kr.spoit.preview.club.TeamPageModels.District;
import co.kr.spoit.preview.club.TeamPageModels.Filters;
import co.kr.spoit.preview.club.TeamPageModels.ManagedClub;
import co.kr.spoit.preview.club.TeamPageModels.ManagedClubSummary;
import co.kr.spoit.preview.club.TeamPageModels.ManagedMember;
import co.kr.spoit.preview.club.TeamPageModels.RecruitPosition;
import co.kr.spoit.preview.club.TeamPageModels.Schedule;
import co.kr.spoit.preview.club.TeamPageModels.SportOption;
import co.kr.spoit.preview.club.TeamPageModels.SportTab;
import co.kr.spoit.preview.club.TeamPageModels.Statistic;
import co.kr.spoit.preview.club.TeamPageModels.TeamMember;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Preview-only fixture controller for the team/club screens.
 */
@Controller
public class TeamPreviewController {

    private static final List<String> TAGS = List.of("주말 매치", "초보 환영", "정기 모임");

    private static final List<District> DISTRICTS = List.of(
            District.builder().regionId(1L).name("서울 강남구").build(),
            District.builder().regionId(2L).name("서울 송파구").build(),
            District.builder().regionId(3L).name("경기 성남시").build());

    private static final List<SportOption> SPORTS = List.of(
            SportOption.builder().sportId(1L).name("배드민턴").build(),
            SportOption.builder().sportId(2L).name("농구").build(),
            SportOption.builder().sportId(3L).name("풋살").build());

    @GetMapping("/teams")
    public String list(Model model) {
        model.addAttribute("sportTabs", List.of(
                SportTab.builder().name("전체").emoji("🏅").url("/teams").active(true).count(12L).build(),
                SportTab.builder().name("배드민턴").emoji("🏸").url("/teams?sportId=1").active(false).count(5L).build(),
                SportTab.builder().name("농구").emoji("🏀").url("/teams?sportId=2").active(false).count(4L).build(),
                SportTab.builder().name("풋살").emoji("⚽").url("/teams?sportId=3").active(false).count(3L).build()));
        model.addAttribute("statistics", List.of(
                Statistic.builder().label("등록 팀").value("12").unit("팀").build(),
                Statistic.builder().label("모집 중").value("8").unit("팀").build(),
                Statistic.builder().label("평균 팀원").value("7.4").unit("명").build()));
        model.addAttribute("filters", Filters.builder()
                .sportId("all")
                .query("")
                .regionId(1L)
                .level(2)
                .sort("recent")
                .recruitingOnly(false)
                .build());
        model.addAttribute("districts", DISTRICTS);
        model.addAttribute("resultCount", 2);
        model.addAttribute("clubs", List.of(clubCard(1L, "강남 셔틀콕", "배드민턴", "서울 강남구", true,
                        "팀원 모집 중", 2, 8, "1,240", "토 09:00 - 11:00", "모집 자리 3명"),
                clubCard(2L, "송파 러너스", "농구", "서울 송파구", false,
                        "모집 마감", 3, 6, "기록 없음", "일 14:00 - 16:00", "모집이 마감되었습니다")));
        return "teams/list";
    }

    @GetMapping("/teams/{clubId}")
    public String detail(@PathVariable Long clubId, Model model) {
        model.addAttribute("club", ClubDetail.builder()
                .clubId(clubId)
                .sportId(1L)
                .initials("강남")
                .clubName("강남 셔틀콕")
                .sportName("배드민턴")
                .areaLabel("서울 강남구")
                .recruiting(true)
                .level(2)
                .tags(TAGS)
                .memberCount(5)
                .maxMembers(8)
                .ratingText("1,240")
                .time("토 09:00 - 11:00")
                .founded("2025.03.14")
                .manager(true)
                .intro("매주 토요일 아침에 모여 배드민턴을 즐기는 팀입니다. 초보자도 편하게 참여할 수 있어요.")
                .leaderName("김스포")
                .leaderInitials("김")
                .recruitPositions(List.of(
                        RecruitPosition.builder().name("남성 복식").count(2).build(),
                        RecruitPosition.builder().name("여성 복식").count(1).build()))
                .member(false)
                .pendingJoinRequestId(12L)
                .members(List.of(
                        TeamMember.builder().initials("김").nickname("김스포").leader(true).build(),
                        TeamMember.builder().initials("이").nickname("이배드").leader(false).build(),
                        TeamMember.builder().initials("박").nickname("박셔틀").leader(false).build(),
                        TeamMember.builder().initials("최").nickname("최콕").leader(false).build(),
                        TeamMember.builder().initials("정").nickname("정민턴").leader(false).build()))
                .build());
        return "teams/detail";
    }

    @GetMapping("/teams/manage")
    public String manage(@RequestParam(name = "tab", defaultValue = "team") String tab, Model model) {
        model.addAttribute("tab", tab);
        model.addAttribute("managedClubs", List.of(
                ManagedClubSummary.builder().clubId(1L).clubName("강남 셔틀콕").memberCount(5).active(true).build()));
        model.addAttribute("managedClub", ManagedClub.builder()
                .clubId(1L)
                .sportId(1L)
                .regionId(1L)
                .initials("강남")
                .clubName("강남 셔틀콕")
                .sportName("배드민턴")
                .areaLabel("서울 강남구")
                .leader(true)
                .memberCount(5)
                .maxMembers(8)
                .leaderName("김스포")
                .founded("2025.03.14")
                .time("토 09:00 - 11:00")
                .intro("매주 토요일 아침에 모여 배드민턴을 즐기는 팀입니다. 초보자도 편하게 참여할 수 있어요.")
                .otherMembersRemain(false)
                .level(2)
                .recruitStatus("recruiting")
                .activityDays(List.of(2, 6))
                .activityStartTime("09:00")
                .activityEndTime("11:00")
                .pendingApplicationCount(0)
                .schedules(List.of(
                        Schedule.builder().date("2026-10-24").dateLabel("10.24 (토)")
                                .title("주말 정기 매치").place("강남 체육관 1코트").time("09:00").build(),
                        Schedule.builder().date("2026-10-31").dateLabel("10.31 (토)")
                                .title("할로윈 복식 대회").place("강남 체육관 2코트").time("10:00").build()))
                .members(List.of(
                        ManagedMember.builder().clubMemberId(101L).initials("김").nickname("김스포")
                                .handle("kimspo").joined("2025.03.14").current(true).leader(true).build(),
                        ManagedMember.builder().clubMemberId(102L).initials("이").nickname("이배드")
                                .handle("leebad").joined("2025.04.02").current(false).leader(false).build(),
                        ManagedMember.builder().clubMemberId(103L).initials("박").nickname("박셔틀")
                                .handle("parkshuttle").joined("2025.05.18").current(false).leader(false).build()))
                // Non-null empty list: the template's th:each variable is named "application",
                // which Thymeleaf 3.1 rejects as a reserved web-context variable when iterated.
                .applications(List.of())
                .build());
        model.addAttribute("sports", SPORTS);
        model.addAttribute("districts", DISTRICTS);
        return "teams/manage";
    }

    private ClubCard clubCard(Long clubId, String clubName, String sportName, String areaLabel,
                              boolean recruiting, String statusLabel, int level, int maxMembers,
                              String ratingText, String time, String footerNote) {
        return ClubCard.builder()
                .clubId(clubId)
                .sportId(1L)
                .initials(clubName.substring(0, 2))
                .clubName(clubName)
                .sportName(sportName)
                .areaLabel(areaLabel)
                .recruiting(recruiting)
                .statusLabel(statusLabel)
                .level(level)
                .tags(TAGS)
                .memberCount(5)
                .maxMembers(maxMembers)
                .ratingText(ratingText)
                .time(time)
                .footerNote(footerNote)
                .build();
    }
}
