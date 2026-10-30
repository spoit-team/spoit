package co.kr.spoit.match.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import co.kr.spoit.match.service.RoomManagementService;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.web.AdminShellModel;

/**
 * 시설 관리자의 매칭 참가자 운영 화면 컨트롤러다.
 *
 * <p>{@code CorePageController}에서 시설 관리자 전용 운영 화면을 분리했다. {@code matchId}가 있으면
 * 인증 주체가 담당하는 시설의 매칭방만 상세로 보여 주고, 없거나 담당하지 않는 방·삭제된 방이면
 * {@code match=null}로 사용할 수 없음 안내를 렌더링한다. 승인·배정·완료 같은 변경 흐름은 아직
 * 구현되어 있지 않다.</p>
 */
@Controller
public class MatchOperationsController {

    private final RoomManagementService roomManagementService;

    public MatchOperationsController(RoomManagementService roomManagementService) {
        this.roomManagementService = roomManagementService;
    }

    /** 운영 관리 화면을 렌더링한다. 상세는 인증 주체의 담당 시설 범위로만 조회한다. */
    @GetMapping("/admin/operations")
    public String adminOperations(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "matchId", required = false) Long matchId, Model model) {
        AdminShellModel.apply(model);
        Long userId = principal == null ? null : principal.getUserId();
        // 클라이언트가 보낸 관리자 ID를 신뢰하지 않고 인증 주체 ID로만 소유권을 확인한다.
        model.addAttribute("match", matchId == null ? null
                : roomManagementService.findDetail(userId, matchId));
        return "admin/operations";
    }
}
