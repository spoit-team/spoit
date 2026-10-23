package co.kr.spoit.member.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.dto.SignupRegion;
import co.kr.spoit.member.service.MemberService;
import lombok.RequiredArgsConstructor;

/**
 * 마이페이지 화면 컨트롤러.
 *
 * <p>로그인 세션의 {@link MemberPrincipal}로 본인 정보를 채운다. 레이팅·경기 이력·알림은
 * 아직 조회 구현이 없어 빈 상태를 유지한다.</p>
 */
@Controller
@RequiredArgsConstructor
public class MyPageController {

    /** 선호 지역이 없을 때 화면에 보여줄 문구 */
    static final String NO_REGION_TEXT = "선호 지역을 설정해 보세요.";

    /** 지역이 여러 개일 때 이어 붙이는 구분자 */
    private static final String REGION_SEPARATOR = " · ";

    private static final List<Object> EMPTY_LIST = List.of();

    private final MemberService memberService;

    /** 마이페이지 요약 화면. 비로그인 상태는 세션 주체가 없어 빈 값으로 렌더링된다. */
    @GetMapping("/mypage")
    public String myPage(Authentication authentication, Model model) {
        MemberPrincipal principal = principalOf(authentication);

        model.addAttribute("member", memberView(principal));
        model.addAttribute("summary", summaryView());
        model.addAttribute("otherSports", EMPTY_LIST);
        model.addAttribute("sports", EMPTY_LIST);
        model.addAttribute("selectedSport", "all");
        model.addAttribute("historyTotalCount", 0);
        model.addAttribute("historyFilterLabel", "전체 종목");
        model.addAttribute("historyCount", 0);
        model.addAttribute("history", EMPTY_LIST);
        model.addAttribute("historyEmptyNote", "확정된 경기 기록이 없습니다.");
        model.addAttribute("currentReservations", EMPTY_LIST);
        model.addAttribute("pastReservations", EMPTY_LIST);
        model.addAttribute("notifications", EMPTY_LIST);
        model.addAttribute("message", null);
        return "mypage/index";
    }

    /** 템플릿이 참조하는 회원 표시값만 담는다. */
    private Map<String, Object> memberView(MemberPrincipal principal) {
        Map<String, Object> member = new LinkedHashMap<>();
        if (principal == null) {
            member.put("initial", "");
            member.put("nickname", "");
            member.put("roleLabel", "");
            member.put("email", "");
            member.put("regionText", NO_REGION_TEXT);
            return member;
        }
        member.put("initial", principal.getInitial());
        member.put("nickname", nullToEmpty(principal.getNickname()));
        member.put("roleLabel", principal.getRoleLabel());
        member.put("email", nullToEmpty(principal.getEmail()));
        member.put("regionText", regionText(principal.getUserId()));
        return member;
    }

    /** 레이팅·경기 집계는 조회 구현 전까지 0건 기본값을 유지한다. */
    private Map<String, Object> summaryView() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("representativeRatingText", "기록 없음");
        summary.put("representativeSportId", "");
        summary.put("representativeSportName", "종목");
        summary.put("skillNote", "참가 확정 매치의 개인 레이팅 평균을 표시합니다.");
        summary.put("confirmedCount", 0);
        summary.put("completedCount", 0);
        summary.put("waitingCount", 0);
        summary.put("otherSportsNote", "매칭 참가 기록이 생기면 종목별 레이팅을 표시합니다.");
        return summary;
    }

    /** 프로필 수정 화면. 로그인 회원이면 저장된 정보를, 아니면 빈 폼을 보여준다. */
    @GetMapping("/mypage/profile")
    public String profile(Authentication authentication, Model model) {
        model.addAttribute("profile", profileView(principalOf(authentication)));
        model.addAttribute("sports", EMPTY_LIST);
        // 선택지가 없으면 저장된 지역을 표시할 수 없으므로 가입 화면과 같은 자치구 목록을 쓴다.
        model.addAttribute("districts", memberService.findSignupRegions());
        model.addAttribute("error", null);
        model.addAttribute("message", null);
        return "mypage/profile";
    }

    /** 프로필 폼이 참조하는 값만 담는다. 비로그인이면 null. */
    private Map<String, Object> profileView(MemberPrincipal principal) {
        if (principal == null) {
            return null;
        }
        List<SignupRegion> regions = memberService.findRegions(principal.getUserId());
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("nickname", nullToEmpty(principal.getNickname()));
        profile.put("email", nullToEmpty(principal.getEmail()));
        profile.put("phone", nullToEmpty(principal.getPhone()));
        profile.put("representativeSportId", null);
        profile.put("representativeRatingText", "기록 없음");
        profile.put("representativeRatingNote", "확정 매치 기록이 없으면 기록 없음으로 표시합니다.");
        profile.put("regionId", regions.isEmpty() ? null : regions.get(0).getRegionId());
        return profile;
    }

    /** 선호 지역 이름을 화면 문구로 만든다. 저장된 지역이 없으면 안내 문구를 쓴다. */
    private String regionText(Long userId) {
        List<SignupRegion> regions = memberService.findRegions(userId);
        if (regions.isEmpty()) {
            return NO_REGION_TEXT;
        }
        return String.join(REGION_SEPARATOR, regions.stream().map(SignupRegion::getName).toList());
    }

    /** 익명 인증은 로그인으로 보지 않는다. */
    private static MemberPrincipal principalOf(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getPrincipal() instanceof MemberPrincipal principal ? principal : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
