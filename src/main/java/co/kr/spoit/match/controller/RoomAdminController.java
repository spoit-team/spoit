package co.kr.spoit.match.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.kr.spoit.facility.service.FacilityScheduleUnavailableException;
import co.kr.spoit.match.dto.RoomForm;
import co.kr.spoit.match.service.RoomCommandException;
import co.kr.spoit.match.service.RoomCommandService;
import co.kr.spoit.member.domain.MemberPrincipal;

/**
 * 시설 관리자의 매칭방 생성·수정·취소 요청을 처리한다.
 *
 * <p>관리자 ID는 인증 주체에서만 가져오고, 결과는 매칭방 관리 섹션으로 되돌아가 안내 문구로
 * 보여 준다. 담당 범위·상태·일정 검증은 {@link RoomCommandService}가 맡는다.</p>
 */
@Controller
public class RoomAdminController {

    /** 처리 후 매칭방 관리 섹션으로 복귀하는 경로. 해시는 `admin-shell.js`의 섹션 ID와 같아야 한다. */
    private static final String BOOKINGS_REDIRECT = "redirect:/admin/facility?view=bookings#facility-bookings";

    private final RoomCommandService roomCommandService;

    public RoomAdminController(RoomCommandService roomCommandService) {
        this.roomCommandService = roomCommandService;
    }

    @PostMapping("/admin/rooms")
    public String createRoom(@AuthenticationPrincipal MemberPrincipal principal,
            RoomForm form, BindingResult bindingResult, RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            redirect.addAttribute("error", "입력 정보를 확인해 주세요.");
            return BOOKINGS_REDIRECT;
        }
        try {
            long matchId = roomCommandService.create(principal.getUserId(), form);
            redirect.addAttribute("message", matchId + "번 매칭방을 만들었습니다.");
        } catch (RoomCommandException | FacilityScheduleUnavailableException exception) {
            redirect.addAttribute("error", exception.getMessage());
        }
        return BOOKINGS_REDIRECT;
    }

    @PostMapping("/admin/rooms/{matchId}")
    public String updateRoom(@PathVariable("matchId") long matchId,
            @AuthenticationPrincipal MemberPrincipal principal,
            RoomForm form, BindingResult bindingResult, RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            redirect.addAttribute("error", "입력 정보를 확인해 주세요.");
            return BOOKINGS_REDIRECT;
        }
        try {
            roomCommandService.update(principal.getUserId(), matchId, form);
            redirect.addAttribute("message", matchId + "번 매칭방을 수정했습니다.");
        } catch (RoomCommandException | FacilityScheduleUnavailableException exception) {
            redirect.addAttribute("error", exception.getMessage());
        }
        return BOOKINGS_REDIRECT;
    }

    @PostMapping("/admin/rooms/{matchId}/cancel")
    public String cancelRoom(@PathVariable("matchId") long matchId,
            @AuthenticationPrincipal MemberPrincipal principal, RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        try {
            roomCommandService.cancel(principal.getUserId(), matchId);
            redirect.addAttribute("message", matchId + "번 매칭방을 취소했습니다.");
        } catch (RoomCommandException | FacilityScheduleUnavailableException exception) {
            redirect.addAttribute("error", exception.getMessage());
        }
        return BOOKINGS_REDIRECT;
    }
}
