package co.kr.spoit.member.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import co.kr.spoit.mat.dto.MyRatingRecords;
import co.kr.spoit.mat.dto.MyRatingRecords.SportCard;
import co.kr.spoit.mat.service.MatchResultPageService;
import co.kr.spoit.mat.service.MyRatingRecordService;
import co.kr.spoit.match.dto.MyReservations;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.match.service.MyReservationService;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.dto.ProfileUpdateForm;
import co.kr.spoit.member.dto.SportOption;
import co.kr.spoit.member.dto.SignupRegion;
import co.kr.spoit.member.dto.WithdrawalBlocks;
import co.kr.spoit.member.dto.WithdrawalRequestForm;
import co.kr.spoit.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 마이페이지 화면 컨트롤러.
 *
 * <p>로그인 세션의 {@link MemberPrincipal}로 본인 정보를 채운다. 선호 종목·프로필 저장을 지원하고,
 * 레이팅은 저장된 {@code sport_rating} 값을 그대로 보여준다. 신청 내역(RES-06)은
 * {@link MyReservationService}가 현재 신청과 지난 참여·취소로 나눠 채운다.</p>
 */
@Controller
@RequiredArgsConstructor
public class MyPageController {

    /** 선호 지역이 없을 때 화면에 보여줄 문구 */
    static final String NO_REGION_TEXT = "선호 지역을 설정해 보세요.";

    /** 지역이 여러 개일 때 이어 붙이는 구분자 */
    private static final String REGION_SEPARATOR = " · ";

    /** 확정된 레이팅 기록이 없을 때 화면에 보여줄 문구 */
    private static final String NO_RATING_TEXT = "기록 없음";

    /** 대표 종목을 정하지 않아 반영 경기가 가장 많은 종목을 대신 보여 줄 때 붙이는 표시 */
    private static final String BY_RECORDS_TEXT = "경기 기록 기준";

    private final MemberService memberService;
    private final MyReservationService reservationService;
    private final MatchApplicationService applicationService;
    private final MatchResultPageService resultPageService;
    private final MyRatingRecordService ratingRecordService;

    /** 폼 바인딩 대상 필드를 제한해 의도하지 않은 값 주입을 막는다. */
    @InitBinder("profileUpdateForm")
    void initProfileUpdateFormBinder(WebDataBinder binder) {
        binder.setAllowedFields(new String[]{"nickname", "phone", "representativeSportId", "regionId"});
    }

    /** 탈퇴 폼은 비밀번호만 받는다. */
    @InitBinder("withdrawalRequestForm")
    void initWithdrawalRequestFormBinder(WebDataBinder binder) {
        binder.setAllowedFields("password");
    }

    /** 마이페이지 요약 화면. 비로그인 상태는 세션 주체가 없어 빈 값으로 렌더링된다. */
    @GetMapping("/mypage")
    public String myPage(Authentication authentication, HttpServletRequest request, Model model,
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "recordSport", required = false) String recordSport) {
        // 신청 취소 폼이 있으므로 지연 생성되는 CSRF 토큰을 렌더링 전에 확정한다.
        // standalone MockMvc에서는 시큐리티 필터가 없어 속성이 비어 있을 수 있다.
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
        MemberPrincipal principal = principalOf(authentication);
        Long userId = principal == null ? null : principal.getUserId();
        MyReservations reservations = (userId == null || userId <= 0)
                ? MyReservations.empty() : reservationService.find(userId);

        // 종목 필터 값이 잘못돼도 오류 없이 전체로 보이도록 문자열로 받아 서비스에 넘긴다.
        MyRatingRecords records = ratingRecordService.find(userId, recordSport);
        // 프로필 영역의 대표·다른 종목 레이팅은 아래 종목 카드와 같은 조회 결과로 그린다.
        Representative representative = representativeOf(userId, records.getSports());

        model.addAttribute("member", memberView(principal));
        model.addAttribute("summary", summaryView(representative, reservations));
        model.addAttribute("otherSports", otherSportsView(records.getSports(), representative.sportId()));
        model.addAttribute("records", records);
        model.addAttribute("currentReservations", reservations.getCurrent());
        model.addAttribute("pastReservations", reservations.getPast());
        model.addAttribute("resultSubmitMatchIds", resultPageService.findResultSubmittableMatchIds(userId));
        // 알림 목록은 GlobalModelAdvice가 로그인 주체 기준으로 채운다. 여기서 덮어쓰지 않는다.
        model.addAttribute("message", blankToNull(message));
        return "mypage/index";
    }

    /**
     * RES-05 신청 취소. 결과는 성공·실패 모두 마이페이지 상단 안내 영역에 보여 준다.
     * RES-04 팀 신청 대표는 취소할 팀원(memberApplicationIds)과, 본인이 빠질 때 새 대표를 함께 보낸다.
     * 팀원을 보내지 않으면 팀 전체를 취소한다.
     */
    @PostMapping("/mypage/applications/{applicationId}/cancel")
    public String cancelApplication(@PathVariable long applicationId,
                                    @RequestParam(required = false) List<Long> memberApplicationIds,
                                    @RequestParam(required = false) Long newLeaderApplicationId,
                                    Authentication authentication) {
        MemberPrincipal principal = principalOf(authentication);
        if (principal == null) {
            return "redirect:/login";
        }
        String message;
        try {
            if (memberApplicationIds == null || memberApplicationIds.isEmpty()) {
                applicationService.cancel(applicationId, principal.getUserId());
            } else {
                applicationService.cancel(applicationId, principal.getUserId(), memberApplicationIds, newLeaderApplicationId);
            }
            message = "신청을 취소했습니다.";
        } catch (MatchApplyException exception) {
            message = exception.getMessage();
        }
        return "redirect:/mypage?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8) + "#reservations";
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

    /** 대표 종목·레이팅과 신청 내역 집계(참가 확정·완료·확정 대기)를 담는다. 종목 이름이 null이면 템플릿이 종목 링크를 그리지 않는다. */
    private Map<String, Object> summaryView(Representative representative, MyReservations reservations) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("representativeRatingText", representative.ratingText());
        summary.put("representativeSportId", representative.sportId() == null ? "" : representative.sportId());
        summary.put("representativeSportName", representative.name());
        summary.put("representativeBasis", representative.byRecords() ? BY_RECORDS_TEXT : null);
        summary.put("skillNote", "확정 경기 결과로 계산한 레이팅을 티어로 표시합니다. 배치 5경기 후 티어가 정해집니다.");
        summary.put("confirmedCount", reservations.getConfirmedCount());
        summary.put("completedCount", reservations.getCompletedCount());
        summary.put("waitingCount", reservations.getWaitingCount());
        summary.put("otherSportsNote", "매칭 참가 기록이 생기면 종목별 레이팅을 표시합니다.");
        return summary;
    }

    /**
     * 설정한 대표 종목을 쓰고, 그 종목에 반영된 경기가 없으면 기록 없음으로 둔다.
     * 설정하지 않았으면 반영 경기가 가장 많은 종목(같으면 종목 번호가 작은 쪽)을 대신 보여 준다.
     * 종목 카드는 반영된 경기가 있는 종목만 담는다.
     */
    private Representative representativeOf(Long userId, List<SportCard> cards) {
        Optional<SportOption> chosen =
                (userId == null || userId <= 0) ? Optional.empty() : memberService.findRepresentativeSport(userId);
        if (chosen.isPresent()) {
            SportOption sport = chosen.get();
            String ratingText = cards.stream()
                    .filter(card -> Objects.equals(card.getSportId(), sport.getSportId()))
                    .map(SportCard::getTierLabel)
                    .findFirst()
                    .orElse(NO_RATING_TEXT);
            return new Representative(sport.getSportId(), sport.getName(), ratingText, false);
        }
        return cards.stream()
                .min(Comparator.comparingInt(SportCard::getRatedMatchCount).reversed()
                        .thenComparing(SportCard::getSportId))
                .map(card -> new Representative(card.getSportId(), card.getName(), card.getTierLabel(), true))
                .orElse(new Representative(null, null, NO_RATING_TEXT, false));
    }

    /** 대표 종목을 뺀, 반영된 경기가 있는 종목. 템플릿의 sport.sportId·name·ratingText·note를 채운다. */
    private static List<Map<String, Object>> otherSportsView(List<SportCard> cards, Long representativeSportId) {
        return cards.stream()
                .filter(card -> !Objects.equals(card.getSportId(), representativeSportId))
                .map(card -> {
                    Map<String, Object> sport = new LinkedHashMap<>();
                    sport.put("sportId", card.getSportId());
                    sport.put("name", card.getName());
                    sport.put("ratingText", card.getTierLabel());
                    sport.put("note", "경기 " + card.getRatedMatchCount() + "회 · " + card.getMmrText());
                    return sport;
                })
                .toList();
    }

    /** 프로필 수정 화면. 로그인 회원이면 저장된 정보를, 아니면 빈 폼을 보여준다. */
    @GetMapping("/mypage/profile")
    public String profile(Authentication authentication, Model model,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "message", required = false) String message) {
        MemberPrincipal principal = principalOf(authentication);
        return renderProfile(model, principal == null ? null : principal.getUserId(),
                profileView(principal), blankToNull(error), blankToNull(message));
    }

    /** 프로필 저장. 검증 실패 시 제출 값을 유지한 채 프로필 화면을 다시 그린다. */
    @PostMapping("/mypage/profile")
    public String updateProfile(@ModelAttribute("profileUpdateForm") ProfileUpdateForm form,
            BindingResult bindingResult, Authentication authentication, Model model) {
        MemberPrincipal principal = principalOf(authentication);
        if (principal == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            return renderProfileError(model, principal, form, "입력 정보를 확인해 주세요.");
        }
        try {
            memberService.updateProfile(principal.getUserId(), form);
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            return renderProfileError(model, principal, form,
                    message == null || message.isBlank() ? "입력 정보를 확인해 주세요." : message);
        }
        return "redirect:/mypage/profile?message=" + URLEncoder.encode("프로필을 저장했습니다.", StandardCharsets.UTF_8);
    }

    /**
     * 회원 탈퇴. 비밀번호를 재확인한 뒤 탈퇴 시각만 기록하는 소프트 삭제이므로
     * 세션과 remember-me 쿠키를 지워 재로그인 경로를 끊는다.
     */
    @PostMapping("/mypage/withdrawal-requests")
    public String withdraw(@ModelAttribute("withdrawalRequestForm") WithdrawalRequestForm form,
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        MemberPrincipal principal = principalOf(authentication);
        if (principal == null) {
            return "redirect:/login";
        }
        try {
            memberService.withdraw(principal.getUserId(), form.getPassword());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // 탈퇴 차단(WithdrawalBlockedException)도 여기로 온다. 로그아웃 전에 반환하므로 세션이 유지된다.
            return redirectProfileError(exception.getMessage());
        }
        new CookieClearingLogoutHandler("JSESSIONID", "remember-me").logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?message=" + URLEncoder.encode("탈퇴가 완료되었습니다.", StandardCharsets.UTF_8);
    }

    /** 탈퇴 실패 사유를 프로필 화면 안내로 되돌린다. */
    private static String redirectProfileError(String message) {
        return "redirect:/mypage/profile?error=" + URLEncoder.encode(
                message == null || message.isBlank() ? "탈퇴를 처리하지 못했습니다." : message,
                StandardCharsets.UTF_8);
    }

    /** 저장 실패 시 제출 값을 유지해 사용자가 수정 내용을 잃지 않게 한다. */
    private String renderProfileError(Model model, MemberPrincipal principal, ProfileUpdateForm form, String error) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("nickname", form == null ? "" : nullToEmpty(form.getNickname()));
        profile.put("phone", form == null ? "" : nullToEmpty(form.getPhone()));
        profile.put("representativeSportId", form == null ? null : form.getRepresentativeSportId());
        profile.put("representativeRatingNote", "확정 매치 기록이 없으면 기록 없음으로 표시합니다.");
        profile.put("regionId", form == null ? null : form.getRegionId());
        profile.put("email", principal.getEmail());
        return renderProfile(model, principal.getUserId(), profile, error, null);
    }

    /** 프로필 화면 모델을 조립한다. 선택지가 없으면 저장된 지역을 표시할 수 없으므로 가입 화면과 같은 자치구 목록을 쓴다. */
    private String renderProfile(Model model, Long userId, Map<String, Object> profile, String error, String message) {
        // 종목 option의 data-rating과 처음 미리보기 문구가 같은 값을 쓰도록 한 번 읽어 둔다.
        Map<Long, String> sportRatings = ratingRecordService.findSportCards(userId).stream()
                .collect(Collectors.toMap(SportCard::getSportId, SportCard::getTierLabel));
        if (profile != null) {
            profile.put("representativeRatingText",
                    sportRatings.getOrDefault(profile.get("representativeSportId"), NO_RATING_TEXT));
        }
        model.addAttribute("profile", profile);
        model.addAttribute("sports", memberService.findSports());
        model.addAttribute("sportRatings", sportRatings);
        model.addAttribute("districts", memberService.findSignupRegions());
        model.addAttribute("error", error);
        model.addAttribute("message", message);
        // 화면과 무관하게 서버가 확인한 탈퇴 차단 사유를 항상 내려준다. 비로그인이면 빈 목록.
        model.addAttribute("withdrawalBlocks", withdrawalBlocks(userId));
        return "mypage/profile";
    }

    /** 탈퇴 차단 사유는 club 도메인 서비스가 판단한다. 조회 결과가 없으면 빈 목록으로 내려준다. */
    private WithdrawalBlocks withdrawalBlocks(Long userId) {
        if (userId == null || userId <= 0) {
            return WithdrawalBlocks.empty();
        }
        WithdrawalBlocks blocks = memberService.findWithdrawalBlocks(userId);
        return blocks == null ? WithdrawalBlocks.empty() : blocks;
    }

    /** 프로필 폼이 참조하는 값만 담는다. 비로그인이면 null. */
    private Map<String, Object> profileView(MemberPrincipal principal) {
        if (principal == null) {
            return null;
        }
        List<SignupRegion> regions = memberService.findRegions(principal.getUserId());
        Optional<SportOption> representative = memberService.findRepresentativeSport(principal.getUserId());
        Member member = memberService.findByEmail(principal.getEmail()).orElse(null);
        Map<String, Object> profile = new LinkedHashMap<>();
        // 세션 principal은 가입 시점 스냅샷이므로 저장된 닉네임·연락처는 DB에서 다시 읽는다.
        profile.put("nickname", member == null ? nullToEmpty(principal.getNickname()) : nullToEmpty(member.getNickname()));
        profile.put("email", nullToEmpty(principal.getEmail()));
        profile.put("phone", member == null ? nullToEmpty(principal.getPhone()) : nullToEmpty(member.getPhone()));
        profile.put("representativeSportId", representative.map(SportOption::getSportId).orElse(null));
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

    /** 쿼리 파라미터의 빈 값을 템플릿의 null 조건에 맞춘다. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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

    /** 프로필 영역에 보여 줄 대표 종목. byRecords는 설정 대신 경기 기록으로 고른 경우다. */
    private record Representative(Long sportId, String name, String ratingText, boolean byRecords) {
    }
}
